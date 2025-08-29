/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
use crate::rtc::RtcError::{SdpError, StatsError};
use std::fmt::Debug;
use std::time::{SystemTime, UNIX_EPOCH};
use tokio::time::Instant;
use webrtc::ice_transport::ice_candidate::{RTCIceCandidate, RTCIceCandidateInit};
use webrtc::ice_transport::ice_connection_state::RTCIceConnectionState;
use webrtc::ice_transport::ice_gatherer_state::RTCIceGathererState;
use webrtc::peer_connection::peer_connection_state::RTCPeerConnectionState;
use webrtc::peer_connection::sdp::sdp_type::RTCSdpType;
use webrtc::peer_connection::sdp::session_description::RTCSessionDescription;
use webrtc::peer_connection::signaling_state::RTCSignalingState;
use webrtc::stats::{RTCStatsType, StatsReportType};

#[derive(uniffi::Record)]
pub struct IceServer {
    pub urls: Vec<String>,
    pub username: String,
    pub credential: String,
}

#[derive(uniffi::Enum)]
pub enum BundlePolicy {
    Balanced,
    MaxCompat,
    MaxBundle,
}

#[derive(uniffi::Enum)]
pub enum RtcpMuxPolicy {
    Negotiate,
    Require,
}

#[derive(uniffi::Enum)]
pub enum IceTransportPolicy {
    All,
    Relay,
}

#[derive(uniffi::Record)]
pub struct ConnectionConfig {
    pub ice_servers: Vec<IceServer>,
    pub ice_candidate_pool_size: u8,
    pub bundle_policy: BundlePolicy,
    pub rtcp_mux_policy: RtcpMuxPolicy,
    pub ice_transport_policy: IceTransportPolicy,
    pub add_default_transceivers: bool,
}

#[derive(uniffi::Enum)]
pub enum SessionDescriptionType {
    Offer,
    Answer,
    ProvisionalAnswer,
    Rollback,
}

#[derive(uniffi::Record)]
pub struct SessionDescription {
    pub sdp_type: SessionDescriptionType,
    pub sdp: String,
}

impl SessionDescription {
    pub(crate) fn from_native(desc: RTCSessionDescription) -> Self {
        SessionDescription {
            sdp_type: match desc.sdp_type {
                RTCSdpType::Unspecified => unreachable!(),
                RTCSdpType::Offer => SessionDescriptionType::Offer,
                RTCSdpType::Answer => SessionDescriptionType::Answer,
                RTCSdpType::Rollback => SessionDescriptionType::Rollback,
                RTCSdpType::Pranswer => SessionDescriptionType::ProvisionalAnswer,
            },
            sdp: desc.sdp,
        }
    }

    pub(crate) fn to_native(&self) -> Result<RTCSessionDescription, RtcError> {
        match self.sdp_type {
            SessionDescriptionType::Offer => RTCSessionDescription::offer(self.sdp.to_owned()),
            SessionDescriptionType::Answer => RTCSessionDescription::answer(self.sdp.to_owned()),
            SessionDescriptionType::Rollback => {
                Err(webrtc::Error::new("Can't set SDP rollback".to_string()))
            }
            SessionDescriptionType::ProvisionalAnswer => {
                RTCSessionDescription::pranswer(self.sdp.to_owned())
            }
        }
        .map_err(|e| SdpError(e.to_string()))
    }
}

#[derive(uniffi::Record)]
pub struct IceCandidate {
    pub candidate: String,
    pub sdp_mid: Option<String>,
    pub sdp_mline_index: Option<u16>,
}

impl IceCandidate {
    pub(crate) fn from_native(candidate: RTCIceCandidate) -> Self {
        let init = candidate.to_json().unwrap();
        IceCandidate {
            candidate: init.candidate,
            sdp_mid: init.sdp_mid,
            sdp_mline_index: init.sdp_mline_index,
        }
    }

    pub(crate) fn to_native(self) -> RTCIceCandidateInit {
        RTCIceCandidateInit {
            candidate: self.candidate,
            sdp_mid: self.sdp_mid,
            sdp_mline_index: self.sdp_mline_index,
            username_fragment: None,
        }
    }
}

#[derive(uniffi::Enum)]
pub enum ConnectionState {
    New,
    Connecting,
    Connected,
    Disconnected,
    Failed,
    Closed,
}

impl ConnectionState {
    pub fn from_native(state: RTCPeerConnectionState) -> Self {
        match state {
            RTCPeerConnectionState::Unspecified => unreachable!(),
            RTCPeerConnectionState::New => ConnectionState::New,
            RTCPeerConnectionState::Connecting => ConnectionState::Connecting,
            RTCPeerConnectionState::Connected => ConnectionState::Connected,
            RTCPeerConnectionState::Disconnected => ConnectionState::Disconnected,
            RTCPeerConnectionState::Failed => ConnectionState::Failed,
            RTCPeerConnectionState::Closed => ConnectionState::Closed,
        }
    }
}

#[derive(uniffi::Enum)]
pub enum IceConnectionState {
    New,
    Checking,
    Connected,
    Completed,
    Failed,
    Disconnected,
    Closed,
}

impl IceConnectionState {
    pub fn from_native(state: RTCIceConnectionState) -> Self {
        match state {
            RTCIceConnectionState::Unspecified => unreachable!(),
            RTCIceConnectionState::New => IceConnectionState::New,
            RTCIceConnectionState::Checking => IceConnectionState::Checking,
            RTCIceConnectionState::Connected => IceConnectionState::Connected,
            RTCIceConnectionState::Completed => IceConnectionState::Completed,
            RTCIceConnectionState::Disconnected => IceConnectionState::Disconnected,
            RTCIceConnectionState::Failed => IceConnectionState::Failed,
            RTCIceConnectionState::Closed => IceConnectionState::Closed,
        }
    }
}

#[derive(uniffi::Enum)]
pub enum IceGatheringState {
    New,
    Gathering,
    Complete,
}

impl IceGatheringState {
    pub fn from_native(state: RTCIceGathererState) -> Self {
        match state {
            RTCIceGathererState::Unspecified => unreachable!(),
            RTCIceGathererState::New => IceGatheringState::New,
            RTCIceGathererState::Gathering => IceGatheringState::Gathering,
            _ => IceGatheringState::Complete,
        }
    }
}

#[derive(uniffi::Enum)]
pub enum SignalingState {
    Stable,
    Closed,
    HaveLocalOffer,
    HaveLocalProvisionalAnswer,
    HaveRemoteOffer,
    HaveRemoteProvisionalAnswer,
}

impl SignalingState {
    pub fn from_native(state: RTCSignalingState) -> Self {
        match state {
            RTCSignalingState::Unspecified => unreachable!(),
            RTCSignalingState::Stable => SignalingState::Stable,
            RTCSignalingState::Closed => SignalingState::Closed,
            RTCSignalingState::HaveLocalOffer => SignalingState::HaveLocalOffer,
            RTCSignalingState::HaveRemoteOffer => SignalingState::HaveRemoteOffer,
            RTCSignalingState::HaveLocalPranswer => SignalingState::HaveLocalProvisionalAnswer,
            RTCSignalingState::HaveRemotePranswer => SignalingState::HaveRemoteProvisionalAnswer,
        }
    }
}

#[derive(uniffi::Record)]
pub struct Stats {
    pub id: String,
    pub timestamp: u64,
    pub type_: String,
    pub props: String,
}

fn instant_to_epoch_millis(instant: &Instant) -> Result<u64, RtcError> {
    let system_now = SystemTime::now();
    let instant_now = Instant::now();
    let approx = system_now - (instant_now - *instant);
    let epoch = approx
        .duration_since(UNIX_EPOCH)
        .map_err(|_| StatsError("Time went backwards".to_string()))?;

    Ok(epoch.as_millis() as u64)
}

fn type_to_string(stats_type: &RTCStatsType) -> Result<String, RtcError> {
    Ok(serde_json::to_string(&stats_type)
        .map_err(|e| StatsError(e.to_string()))?
        .trim_matches('"') // Remove the quotes from a JSON string
        .to_string())
}

impl Stats {
    pub fn from_native(report: &StatsReportType) -> Result<Self, RtcError> {
        macro_rules! create_stats {
            ($report:expr) => {
                Self {
                    id: $report.id.clone(),
                    type_: type_to_string(&$report.stats_type)?,
                    timestamp: instant_to_epoch_millis(&$report.timestamp)?,
                    props: serde_json::to_string(&$report)
                        .map_err(|e| StatsError(e.to_string()))?,
                }
            };
        }

        let stats = match report {
            StatsReportType::CandidatePair(r) => create_stats!(r),
            StatsReportType::CertificateStats(r) => create_stats!(r),
            StatsReportType::Codec(r) => create_stats!(r),
            StatsReportType::DataChannel(r) => create_stats!(r),
            StatsReportType::LocalCandidate(r) => create_stats!(r),
            StatsReportType::PeerConnection(r) => create_stats!(r),
            StatsReportType::RemoteCandidate(r) => create_stats!(r),
            StatsReportType::SCTPTransport(r) => create_stats!(r),
            StatsReportType::Transport(r) => create_stats!(r),
            StatsReportType::InboundRTP(r) => create_stats!(r),
            StatsReportType::OutboundRTP(r) => create_stats!(r),
            StatsReportType::RemoteInboundRTP(r) => create_stats!(r),
            StatsReportType::RemoteOutboundRTP(r) => create_stats!(r),
        };
        Ok(stats)
    }
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum RtcError {
    #[error("Failed to initialize RTC Connection: {0}")]
    CreateConnectionError(String),

    #[error("SDP Error: {0}")]
    SdpError(String),

    #[error("Failed to add an ICE candidate: {0}")]
    IceError(String),

    #[error("Failed to add a track: {0}")]
    AddTrackError(String),

    #[error("Failed to remove a track: {0}")]
    RemoveTrackError(String),

    #[error("Data channel error: {0}")]
    DataChannelError(String),

    #[error("Failed to retrieve stats: {0}")]
    StatsError(String),

    #[error("Feature is not supported by WebRTC.rs")]
    FeatureNotSupported,

    #[error("Dtmf is not supported by WebRTC.rs")]
    DtmfNotSupported,

    #[error("Media track error: {0}")]
    MediaTrackError(String),

    #[error("Media track type error: {0}")]
    MediaTrackTypeError(String),
}
