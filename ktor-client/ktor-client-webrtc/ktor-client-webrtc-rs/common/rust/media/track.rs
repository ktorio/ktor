/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
use crate::media::{MediaCodec, MediaStreamSinkWrapper, MediaStreamTrack, MediaStreamTrackInner};
use crate::rtc::RtcError;
use crate::rtc::RtcError::MediaTrackError;
use arc_swap::ArcSwapOption;
use bytes::Bytes;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Duration;
use tokio::sync::Mutex;
use webrtc::api::media_engine::{MIME_TYPE_H264, MIME_TYPE_OPUS, MIME_TYPE_VP8};
use webrtc::media::Sample;
use webrtc::rtp_transceiver::rtp_codec::RTCRtpCodecCapability;
use webrtc::track::track_local::TrackLocal;
use webrtc::track::track_local::track_local_static_sample::TrackLocalStaticSample;
use webrtc::track::track_remote::TrackRemote;

const VIDEO_CLOCK_RATE: u32 = 90000;
const AUDIO_CLOCK_RATE: u32 = 48000;

impl MediaStreamTrack {
    pub fn from_local(track: Arc<TrackLocalStaticSample>) -> Result<Self, RtcError> {
        Ok(MediaStreamTrack {
            id: track.id().to_string(),
            read_mutex: Mutex::new(()),
            enabled: AtomicBool::new(true),
            clock_rate: track.codec().clock_rate,
            codec: MediaCodec::from(track.codec().mime_type.as_str())?,
            inner: MediaStreamTrackInner::LocalSample(track),
            sink_wrapper: ArcSwapOption::new(None),
        })
    }

    pub fn from_remote(track: Arc<TrackRemote>) -> Result<Self, RtcError> {
        Ok(MediaStreamTrack {
            id: track.id().to_string(),
            read_mutex: Mutex::new(()),
            enabled: AtomicBool::new(true),
            clock_rate: track.codec().capability.clock_rate,
            codec: MediaCodec::from(&track.codec().capability.mime_type)?,
            inner: MediaStreamTrackInner::Remote(track),
            sink_wrapper: ArcSwapOption::new(None),
        })
    }

    fn with_capability(
        capability: RTCRtpCodecCapability,
        track_id: &str,
        stream_id: &str,
    ) -> Result<Self, RtcError> {
        let track = Arc::new(TrackLocalStaticSample::new(
            capability,
            track_id.to_owned(),
            stream_id.to_owned(),
        ));

        Self::from_local(track)
    }

    /// Get underlying TrackLocal for adding to PeerConnection
    pub fn as_local(&self) -> Result<Arc<TrackLocalStaticSample>, RtcError> {
        match &self.inner {
            MediaStreamTrackInner::LocalSample(t) => Ok(Arc::clone(t)),
            _ => Err(MediaTrackError(
                "MediaStreamTrack is not local.".to_string(),
            )),
        }
    }

    pub fn as_local_trait(&self) -> Result<Arc<dyn TrackLocal + Send + Sync>, RtcError> {
        Ok(self.as_local()?)
    }

    /// Get underlying TrackRemote
    pub fn as_remote(&self) -> Result<Arc<TrackRemote>, RtcError> {
        match &self.inner {
            MediaStreamTrackInner::Remote(t) => Ok(Arc::clone(t)),
            _ => Err(MediaTrackError(
                "MediaStreamTrack is not remote.".to_string(),
            )),
        }
    }
}

#[uniffi::export]
pub fn create_video_vp8_track(
    track_id: &str,
    stream_id: &str,
) -> Result<MediaStreamTrack, RtcError> {
    let capability = RTCRtpCodecCapability {
        mime_type: MIME_TYPE_VP8.to_owned(),
        clock_rate: VIDEO_CLOCK_RATE,
        sdp_fmtp_line: "".to_owned(),
        rtcp_feedback: vec![],
        channels: 0,
    };
    MediaStreamTrack::with_capability(capability, track_id, stream_id)
}

#[uniffi::export]
pub fn create_video_h264_track(
    track_id: &str,
    stream_id: &str,
) -> Result<MediaStreamTrack, RtcError> {
    let capability = RTCRtpCodecCapability {
        mime_type: MIME_TYPE_H264.to_owned(),
        clock_rate: VIDEO_CLOCK_RATE,
        sdp_fmtp_line: "".to_owned(),
        rtcp_feedback: vec![],
        channels: 0,
    };
    MediaStreamTrack::with_capability(capability, track_id, stream_id)
}

#[uniffi::export]
pub fn create_audio_opus_track(
    track_id: &str,
    stream_id: &str,
) -> Result<MediaStreamTrack, RtcError> {
    let capability = RTCRtpCodecCapability {
        mime_type: MIME_TYPE_OPUS.to_owned(),
        clock_rate: AUDIO_CLOCK_RATE,
        sdp_fmtp_line: "".to_owned(),
        rtcp_feedback: vec![],
        channels: 2,
    };
    MediaStreamTrack::with_capability(capability, track_id, stream_id)
}

#[uniffi::export(async_runtime = "tokio")]
impl MediaStreamTrack {
    pub fn id(&self) -> String {
        self.id.clone()
    }

    pub fn codec(&self) -> MediaCodec {
        self.codec.clone()
    }

    pub fn clock_rate(&self) -> u32 {
        self.clock_rate
    }

    pub fn enabled(&self) -> bool {
        self.enabled.load(Ordering::Relaxed)
    }

    pub fn is_local(&self) -> bool {
        !self.is_remote()
    }

    pub fn is_remote(&self) -> bool {
        match self.inner {
            MediaStreamTrackInner::LocalSample(_) => false,
            MediaStreamTrackInner::Remote(_) => true,
        }
    }

    pub fn set_enabled(&self, enabled: bool) {
        self.enabled.store(enabled, Ordering::Relaxed);
    }

    pub async fn write_data(&self, data: Vec<u8>, duration: Duration) -> Result<(), RtcError> {
        let track = self.as_local()?;
        if !self.enabled() {
            return Ok(());
        }
        let sample = Sample {
            duration,
            data: Bytes::from(data),
            ..Default::default()
        };
        track
            .write_sample(&sample)
            .await
            .map_err(|e| MediaTrackError(e.to_string()))
    }

    pub async fn has_sink(&self) -> bool {
        self.sink_wrapper.load().is_some()
    }

    pub fn set_sink(&self, wrapper: Option<Arc<MediaStreamSinkWrapper>>) {
        self.sink_wrapper.swap(wrapper);
    }

    /// Read RTP packet from a remote track and write it to sink
    /// Only one `read_all` and `read` is allowed at a time, otherwise it will return an error
    pub async fn read_rtp(&self) -> Result<(), RtcError> {
        let track = self.as_remote()?;
        if !self.enabled() {
            return Ok(());
        }
        let (rtp_packet, _) = {
            let _ = self
                .read_mutex
                .try_lock()
                .map_err(|_| MediaTrackError("Parallel read is not allowed".to_string()))?;
            track
                .read_rtp()
                .await
                .map_err(|e| MediaTrackError(e.to_string()))?
        };
        if let Some(wrapper) = self.sink_wrapper.load_full() {
            wrapper.sink.write_rtp(rtp_packet).await?;
        }
        Ok(())
    }

    /// Read all RTP packets from a remote track and write them to sink if available at a time a packet arrives
    /// Only one `read_all` and `read` is allowed at a time, otherwise it will return an error
    pub async fn read_all(&self) -> Result<(), RtcError> {
        let track = self.as_remote()?;
        let _ = self
            .read_mutex
            .try_lock()
            .map_err(|_| MediaTrackError("Parallel read is not allowed".to_string()))?;
        loop {
            match track.read_rtp().await {
                Ok((rtp_packet, _)) => {
                    if !self.enabled() {
                        continue;
                    }
                    if let Some(wrapper) = self.sink_wrapper.load_full() {
                        wrapper.sink.write_rtp(rtp_packet).await?;
                    }
                }
                Err(_) => return Ok(()),
            }
        }
    }
}
