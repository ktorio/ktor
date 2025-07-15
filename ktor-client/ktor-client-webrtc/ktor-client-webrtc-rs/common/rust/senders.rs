/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
use crate::media::MediaStreamTrack;
use crate::rtc::RtcError;
use crate::rtc::RtcError::MediaTrackError;
use arc_swap::ArcSwapOption;
use std::sync::Arc;
use webrtc::rtp_transceiver::rtp_codec::{RTCRtpCodecParameters, RTCRtpHeaderExtensionParameters};
use webrtc::rtp_transceiver::rtp_sender::RTCRtpSender;
use webrtc::rtp_transceiver::{PayloadType, RTCRtpCodingParameters, RTCRtpSendParameters, SSRC};

#[derive(uniffi::Object)]
pub struct RtpSender {
    pub inner: Arc<RTCRtpSender>,
    pub track: ArcSwapOption<MediaStreamTrack>,
}

impl RtpSender {
    pub fn new(inner: Arc<RTCRtpSender>, track: Arc<MediaStreamTrack>) -> Self {
        Self {
            inner,
            track: ArcSwapOption::new(Some(track)),
        }
    }
}

#[uniffi::export(async_runtime = "tokio")]
impl RtpSender {
    /// Get the track being sent by this RtpSender, if any
    pub async fn track(&self) -> Option<Arc<MediaStreamTrack>> {
        self.track.load_full()
    }

    pub async fn set_track(&self, track: Option<Arc<MediaStreamTrack>>) -> Result<(), RtcError> {
        self.inner
            .replace_track(track.map(|t| t.as_local_trait().unwrap()))
            .await
            .map_err(|e| MediaTrackError(e.to_string()))
    }

    pub async fn get_parameters(&self) -> RtpParameters {
        RtpParameters::from_native(self.inner.get_parameters().await)
    }
}

#[derive(uniffi::Record)]
pub struct RtpParameters {
    pub encodings: Vec<RtpEncodingParameters>,
    pub header_extensions: Vec<RtpHeaderExtensionParameters>,
    pub codecs: Vec<RtpCodecParameters>,
    pub degradation_preference: DegradationPreference,
}

impl RtpParameters {
    pub fn from_native(params: RTCRtpSendParameters) -> Self {
        Self {
            encodings: params
                .encodings
                .into_iter()
                .map(RtpEncodingParameters::from_native)
                .collect(),
            header_extensions: params
                .rtp_parameters
                .header_extensions
                .into_iter()
                .map(RtpHeaderExtensionParameters::from_native)
                .collect(),
            codecs: params
                .rtp_parameters
                .codecs
                .into_iter()
                .map(RtpCodecParameters::from_native)
                .collect(),
            degradation_preference: DegradationPreference::Balanced, // Default value
        }
    }
}

#[derive(uniffi::Record)]
pub struct RtpEncodingParameters {
    pub rid: String,
    pub ssrc: SSRC,
    pub payload_type: PayloadType,
    pub rtx: SSRC,
}

impl RtpEncodingParameters {
    pub fn from_native(params: RTCRtpCodingParameters) -> Self {
        Self {
            rid: params.rid.to_string(),
            ssrc: params.ssrc,
            payload_type: params.payload_type,
            rtx: params.rtx.ssrc,
        }
    }
}

#[derive(uniffi::Record)]
pub struct RtpHeaderExtensionParameters {
    pub id: i64,
    pub uri: String,
    pub encrypted: bool,
}

impl RtpHeaderExtensionParameters {
    pub fn from_native(params: RTCRtpHeaderExtensionParameters) -> Self {
        Self {
            id: params.id as i64,
            uri: params.uri,
            encrypted: false,
        }
    }
}

#[derive(uniffi::Record, Clone)]
pub struct RtpCodecParameters {
    pub payload_type: u8,
    pub mime_type: String,
    pub clock_rate: u32,
    pub channels: u16,
    pub sdp_fmtp_line: String,
}

impl RtpCodecParameters {
    pub fn from_native(params: RTCRtpCodecParameters) -> Self {
        Self {
            payload_type: params.payload_type,
            mime_type: params.capability.mime_type,
            clock_rate: params.capability.clock_rate,
            channels: params.capability.channels,
            sdp_fmtp_line: params.capability.sdp_fmtp_line,
        }
    }
}

#[derive(uniffi::Enum, Clone)]
pub enum DegradationPreference {
    Balanced,
    MaintainFramerate,
    MaintainResolution,
    Disabled,
}
