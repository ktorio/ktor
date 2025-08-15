mod connection;
mod datachannel;
mod media;
mod rtc;
mod senders;

use crate::connection::PeerConnection;
use crate::rtc::RtcError::CreateConnectionError;
use crate::rtc::{BundlePolicy, ConnectionConfig, IceTransportPolicy, RtcError, RtcpMuxPolicy};
use std::sync::Arc;
use webrtc::api::interceptor_registry::register_default_interceptors;
use webrtc::api::media_engine::MediaEngine;
use webrtc::api::APIBuilder;
use webrtc::ice_transport::ice_server::RTCIceServer;
use webrtc::interceptor::registry::Registry;
use webrtc::peer_connection::configuration::RTCConfiguration;
use webrtc::peer_connection::policy::bundle_policy::RTCBundlePolicy;
use webrtc::peer_connection::policy::ice_transport_policy::RTCIceTransportPolicy;
use webrtc::peer_connection::policy::rtcp_mux_policy::RTCRtcpMuxPolicy;
use webrtc::rtp_transceiver::rtp_codec::RTPCodecType;
use webrtc::rtp_transceiver::rtp_transceiver_direction::RTCRtpTransceiverDirection;
use webrtc::rtp_transceiver::RTCRtpTransceiverInit;
use webrtc::Error;

fn with_create_connection_error(e: Error) -> RtcError {
    CreateConnectionError(e.to_string())
}

#[uniffi::export]
fn enable_logging() {
    use tracing_subscriber::{fmt, EnvFilter};
    let env_filter =
        EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("webrtc=trace"));
    let _ = fmt().with_env_filter(env_filter).try_init(); // ignore AlreadyInit errors
}

#[uniffi::export(async_runtime = "tokio")]
async fn make_peer_connection(config: ConnectionConfig) -> Result<Arc<PeerConnection>, RtcError> {
    let rtc_config = RTCConfiguration {
        ice_servers: config
            .ice_servers
            .iter()
            .map(|s| RTCIceServer {
                urls: s.urls.clone(),
                username: s.username.clone(),
                credential: s.credential.clone(),
            })
            .collect(),
        ice_candidate_pool_size: config.ice_candidate_pool_size,
        bundle_policy: match config.bundle_policy {
            BundlePolicy::Balanced => RTCBundlePolicy::Balanced,
            BundlePolicy::MaxCompat => RTCBundlePolicy::MaxCompat,
            BundlePolicy::MaxBundle => RTCBundlePolicy::MaxBundle,
        },
        rtcp_mux_policy: match config.rtcp_mux_policy {
            RtcpMuxPolicy::Negotiate => RTCRtcpMuxPolicy::Negotiate,
            RtcpMuxPolicy::Require => RTCRtcpMuxPolicy::Require,
        },
        peer_identity: "".to_string(),
        ice_transport_policy: match config.ice_transport_policy {
            IceTransportPolicy::All => RTCIceTransportPolicy::All,
            IceTransportPolicy::Relay => RTCIceTransportPolicy::Relay,
        },
        certificates: vec![],
    };

    // Create a MediaEngine object to configure the supported codec
    let mut m = MediaEngine::default();
    m.register_default_codecs()
        .map_err(with_create_connection_error)?;

    // Use the default set of Interceptors
    let registry = register_default_interceptors(Registry::new(), &mut m)
        .map_err(with_create_connection_error)?;

    // Create the API object with the MediaEngine
    let api = APIBuilder::new()
        .with_media_engine(m)
        .with_interceptor_registry(registry)
        .build();

    // Create a new RTCPeerConnection
    let inner = api
        .new_peer_connection(rtc_config)
        .await
        .map_err(with_create_connection_error)?;

    // Add a default audio and video transceivers (send + recv) to emulate browser behavior
    if config.add_default_transceivers {
        inner
            .add_transceiver_from_kind(
                RTPCodecType::Audio,
                Some(RTCRtpTransceiverInit {
                    direction: RTCRtpTransceiverDirection::Sendrecv,
                    send_encodings: vec![],
                }),
            )
            .await
            .map_err(with_create_connection_error)?;

        inner
            .add_transceiver_from_kind(
                RTPCodecType::Video,
                Some(RTCRtpTransceiverInit {
                    direction: RTCRtpTransceiverDirection::Sendrecv,
                    send_encodings: vec![],
                }),
            )
            .await
            .map_err(with_create_connection_error)?;
    }

    Ok(Arc::new(PeerConnection::new(inner)))
}

uniffi::setup_scaffolding!();
