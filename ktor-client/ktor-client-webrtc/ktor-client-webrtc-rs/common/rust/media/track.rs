/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
use crate::media::{
    CodecMimeType, MediaKind, MediaStreamSink, MediaStreamTrack, MediaStreamTrackInner, TrackSample,
};
use crate::rtc::RtcError;
use crate::rtc::RtcError::MediaTrackError;
use arc_swap::ArcSwap;
use std::sync::Arc;
use std::sync::atomic::Ordering;
use std::time::Duration;
use uniffi::deps::bytes::Bytes;
use webrtc::api::media_engine::{MIME_TYPE_H264, MIME_TYPE_OPUS, MIME_TYPE_VP8};
use webrtc::media::Sample;
use webrtc::rtp_transceiver::rtp_codec::RTCRtpCodecCapability;
use webrtc::track::track_local::TrackLocal;
use webrtc::track::track_local::track_local_static_sample::TrackLocalStaticSample;
use webrtc::track::track_remote::TrackRemote;

impl MediaStreamTrack {
    pub fn from_local(track: Arc<TrackLocalStaticSample>) -> Self {
        let kind = if track.codec().mime_type.starts_with("audio/") {
            MediaKind::Audio
        } else {
            MediaKind::Video
        };
        MediaStreamTrack {
            kind,
            enabled: true.into(),
            id: track.id().to_string(),
            inner: MediaStreamTrackInner::LocalSample(track),
            sink: ArcSwap::new(MediaStreamSink::empty().into()),
        }
    }

    pub fn from_remote(track: Arc<TrackRemote>) -> MediaStreamTrack {
        let kind = if track.codec().capability.mime_type.starts_with("audio/") {
            MediaKind::Audio
        } else {
            MediaKind::Video
        };
        MediaStreamTrack {
            kind,
            enabled: true.into(),
            id: track.id().to_string(),
            inner: MediaStreamTrackInner::Remote(track),
            sink: ArcSwap::new(MediaStreamSink::empty().into()),
        }
    }

    fn with_capability(capability: RTCRtpCodecCapability, track_id: &str, stream_id: &str) -> Self {
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
            MediaStreamTrackInner::LocalSample(t) => Ok(t.clone()),
            _ => Err(MediaTrackError(
                "MediaStreamTrack is not local.".to_string(),
            )),
        }
    }

    pub fn as_local_trait(&self) -> Result<Arc<dyn TrackLocal + Send + Sync>, RtcError> {
        Ok(self.as_local()? as Arc<dyn TrackLocal + Send + Sync>)
    }

    /// Get underlying TrackRemote
    pub fn as_remote(&self) -> Result<Arc<TrackRemote>, RtcError> {
        match &self.inner {
            MediaStreamTrackInner::Remote(t) => Ok(t.clone()),
            _ => Err(MediaTrackError(
                "MediaStreamTrack is not remote.".to_string(),
            )),
        }
    }
}

#[uniffi::export]
pub fn create_video_track(
    codec_mime: CodecMimeType,
    track_id: &str,
    stream_id: &str,
) -> Result<MediaStreamTrack, RtcError> {
    let capability = match codec_mime {
        CodecMimeType::VideoVP8 => RTCRtpCodecCapability {
            mime_type: MIME_TYPE_VP8.to_owned(),
            clock_rate: 90000,
            channels: 0,
            sdp_fmtp_line: "".to_owned(),
            rtcp_feedback: vec![],
        },
        CodecMimeType::VideoH264 => RTCRtpCodecCapability {
            mime_type: MIME_TYPE_H264.to_owned(),
            clock_rate: 90000,
            channels: 0,
            sdp_fmtp_line: "".to_owned(),
            rtcp_feedback: vec![],
        },
        _ => {
            return Err(MediaTrackError(format!(
                "Unsupported video codec: {:?}",
                codec_mime
            )));
        }
    };
    Ok(MediaStreamTrack::with_capability(
        capability, track_id, stream_id,
    ))
}

#[uniffi::export]
pub fn create_audio_track(
    codec_mime: CodecMimeType,
    track_id: &str,
    stream_id: &str,
) -> Result<MediaStreamTrack, RtcError> {
    let capability = match codec_mime {
        CodecMimeType::AudioOpus => RTCRtpCodecCapability {
            mime_type: MIME_TYPE_OPUS.to_owned(),
            clock_rate: 48000,
            channels: 2,
            sdp_fmtp_line: "minptime=10;useinbandfec=1".to_owned(),
            ..Default::default()
        },
        _ => {
            return Err(MediaTrackError(format!(
                "Unsupported audio codec: {:?}",
                codec_mime
            )));
        }
    };
    Ok(MediaStreamTrack::with_capability(
        capability, track_id, stream_id,
    ))
}

#[uniffi::export(async_runtime = "tokio")]
impl MediaStreamTrack {
    pub fn id(&self) -> String {
        self.id.clone()
    }

    pub fn kind(&self) -> MediaKind {
        self.kind.clone()
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

    pub async fn write_sample(&self, sample: TrackSample) -> Result<(), RtcError> {
        let track = self.as_local()?;
        if !self.enabled() {
            return Ok(());
        }
        let rtc_sample = Sample {
            data: Bytes::from(sample.data),
            timestamp: sample.timestamp,
            duration: sample.duration,
            packet_timestamp: sample.packet_timestamp,
            prev_dropped_packets: sample.prev_dropped_packets,
            prev_padding_packets: sample.prev_padding_packets,
        };
        track
            .write_sample(&rtc_sample)
            .await
            .map_err(|e| MediaTrackError(e.to_string()))
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

    pub async fn set_sink(&self, sink: Arc<MediaStreamSink>) {
        self.sink.store(sink)
    }

    /// Read RTP packet from a remote track and write it to sink
    pub async fn read_rtp(&self) -> Result<(), RtcError> {
        let track = self.as_remote()?;
        if !self.enabled() {
            return Ok(());
        }
        let (rtp_packet, _) = track
            .read_rtp()
            .await
            .map_err(|e| MediaTrackError(e.to_string()))?;

        if let Some(writer) = &self.sink.load().writer {
            let mut w = writer.lock().await;
            w.write_rtp(&rtp_packet)
                .map_err(|e| MediaTrackError(e.to_string()))?;
        }
        Ok(())
    }

    pub async fn read_all(&self) -> Result<(), RtcError> {
        let track = self.as_remote()?;
        if let Some(writer) = &self.sink.load().writer {
            loop {
                match track.read_rtp().await {
                    Ok((rtp_packet, _)) => {
                        if !self.enabled() {
                            continue;
                        }
                        let mut w = writer.lock().await;
                        w.write_rtp(&rtp_packet)
                            .map_err(|e| MediaTrackError(e.to_string()))?;
                    }
                    Err(_) => {
                        let mut w = writer.lock().await;
                        w.close().map_err(|e| MediaTrackError(e.to_string()))?;
                        return Ok(());
                    }
                }
            }
        }
        Ok(())
    }
}
