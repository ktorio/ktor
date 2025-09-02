/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

use crate::media::{
    AsyncWriter, MediaCodec, MediaHandler, MediaSample, MediaStreamSink,
    MediaStreamSinkWrapper, MediaStreamTrack,
};
use crate::rtc::RtcError;
use async_trait::async_trait;
use std::sync::Arc;
use tokio::sync::Mutex;
use webrtc::rtp::codecs::h264::H264Packet;
use webrtc::rtp::codecs::opus::OpusPacket;
use webrtc::rtp::codecs::vp8::Vp8Packet;
use webrtc::rtp::packet::Packet;
use webrtc::rtp::packetizer::Depacketizer;
use webrtc_media::io::sample_builder::SampleBuilder;

const VIDEO_MAX_LATE: u16 = 64;
const AUDIO_MAX_LATE: u16 = 32;

/// Sample sink that uses SampleBuilder to reconstruct complete samples from RTP packets

impl<T: Depacketizer + Send + Sync> MediaStreamSink<T> {
    pub fn new(
        max_late: u16,
        depacketizer: T,
        sample_rate: u32,
        handler: Arc<dyn MediaHandler>,
    ) -> Self {
        let sample_builder = SampleBuilder::new(max_late, depacketizer, sample_rate);
        Self {
            sample_builder: Arc::new(Mutex::new(sample_builder)),
            handler,
        }
    }
}

#[async_trait]
impl<T: Depacketizer + Send + Sync> AsyncWriter for MediaStreamSink<T> {
    async fn write_rtp(&self, pkt: Packet) -> Result<(), RtcError> {
        let mut builder = self.sample_builder.lock().await;
        builder.push(pkt);

        // Process all available samples
        while let Some(sample) = builder.pop() {
            let track_sample = MediaSample {
                data: sample.data.to_vec(),
                timestamp: sample.timestamp,
                duration: sample.duration,
                packet_timestamp: sample.packet_timestamp,
                prev_dropped_packets: sample.prev_dropped_packets,
                prev_padding_packets: sample.prev_padding_packets,
            };
            self.handler.on_next_sample(track_sample);
        }

        Ok(())
    }
}

#[uniffi::export]
impl MediaStreamTrack {
    pub fn create_sink(
        &self,
        handler: Arc<dyn MediaHandler>,
    ) -> Result<Arc<MediaStreamSinkWrapper>, RtcError> {
        self.as_remote()?;
        let sink: Arc<dyn AsyncWriter> = match self.codec {
            MediaCodec::VideoVP8 => self.create_vp8_video_sink(handler),
            MediaCodec::VideoH264 => self.create_h264_video_sink(handler),
            MediaCodec::AudioOpus => self.create_opus_audio_sink(handler),
        };
        Ok(Arc::new(MediaStreamSinkWrapper { sink }))
    }
}
impl MediaStreamTrack {
    fn create_h264_video_sink(
        &self,
        handler: Arc<dyn MediaHandler>,
    ) -> Arc<MediaStreamSink<H264Packet>> {
        let depacketizer = H264Packet::default();
        Arc::new(MediaStreamSink::new(
            VIDEO_MAX_LATE,
            depacketizer,
            self.clock_rate,
            handler,
        ))
    }

    fn create_vp8_video_sink(
        &self,
        handler: Arc<dyn MediaHandler>,
    ) -> Arc<MediaStreamSink<Vp8Packet>> {
        let depacketizer = Vp8Packet::default();
        Arc::new(MediaStreamSink::new(
            VIDEO_MAX_LATE,
            depacketizer,
            self.clock_rate,
            handler,
        ))
    }

    fn create_opus_audio_sink(
        &self,
        handler: Arc<dyn MediaHandler>,
    ) -> Arc<MediaStreamSink<OpusPacket>> {
        let depacketizer = OpusPacket::default();
        Arc::new(MediaStreamSink::new(
            AUDIO_MAX_LATE,
            depacketizer,
            self.clock_rate,
            handler,
        ))
    }
}
