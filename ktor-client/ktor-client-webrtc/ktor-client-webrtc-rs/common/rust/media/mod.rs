/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
mod sink;
mod track;

use crate::media::MediaCodec::{AudioOpus, VideoH264, VideoVP8};
use crate::rtc::RtcError;
use crate::rtc::RtcError::MediaTrackError;
use arc_swap::ArcSwapOption;
use async_trait::async_trait;
use std::fmt::Debug;
use std::sync::Arc;
use std::sync::atomic::AtomicBool;
use std::time::{Duration, SystemTime};
use tokio::sync::Mutex;
use webrtc::media::io::sample_builder::SampleBuilder;
use webrtc::rtp::packet::Packet;
use webrtc::rtp::packetizer::Depacketizer;
use webrtc::track::track_local::track_local_static_sample::TrackLocalStaticSample;
use webrtc::track::track_remote::TrackRemote;

// The same as webrtc_media::io::Writer, but async
#[async_trait]
pub trait AsyncWriter: Send + Sync {
    // Add the content of an RTP packet to the media
    async fn write_rtp(&self, pkt: Packet) -> Result<(), RtcError>;
}

// Wrapper for AsyncWriter to be used as a field in MediaStreamTrack
// It is needed to be able to pass it to the Kotlin side
// MediaStreamTrack can't be generic, so we need to use a wrapper for the sink
#[derive(uniffi::Object)]
pub struct MediaStreamSinkWrapper {
    sink: Arc<dyn AsyncWriter>,
}

#[derive(uniffi::Object)]
pub struct MediaStreamTrack {
    id: String,
    clock_rate: u32,
    codec: MediaCodec,
    enabled: AtomicBool,
    read_mutex: Mutex<()>,
    inner: MediaStreamTrackInner,
    sink_wrapper: ArcSwapOption<MediaStreamSinkWrapper>,
}

pub struct MediaStreamSink<T: Depacketizer + Send + Sync> {
    sample_builder: Arc<Mutex<SampleBuilder<T>>>,
    handler: Arc<dyn MediaHandler>,
}

#[derive(uniffi::Record)]
pub struct MediaSample {
    pub data: Vec<u8>,
    pub timestamp: SystemTime,
    pub duration: Duration,
    pub packet_timestamp: u32,
    pub prev_dropped_packets: u16,
    pub prev_padding_packets: u16,
}

#[uniffi::export(with_foreign)]
pub trait MediaHandler: Send + Sync + Debug {
    /// Called when parsed media data is available
    fn on_next_sample(&self, sample: MediaSample);
}

enum MediaStreamTrackInner {
    LocalSample(Arc<TrackLocalStaticSample>),
    Remote(Arc<TrackRemote>),
}

#[derive(uniffi::Enum, Debug, Clone)]
pub enum MediaCodec {
    VideoVP8,
    VideoH264,
    AudioOpus,
}

impl MediaCodec {
    fn from(mime_type: &str) -> Result<Self, RtcError> {
        match mime_type {
            "video/VP8" => Ok(VideoVP8),
            "video/H264" => Ok(VideoH264),
            "audio/opus" => Ok(AudioOpus),
            _ => Err(MediaTrackError(format!(
                "Received unknown track codec {}",
                mime_type
            ))),
        }
    }
}
