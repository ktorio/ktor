/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
use crate::datachannel::{DataChannel, DataChannelInit};
use crate::media::MediaStreamTrack;
use crate::rtc::RtcError::{AddTrackError, DataChannelError, IceError, RemoveTrackError, SdpError};
use crate::rtc::{
    ConnectionState, IceCandidate, IceConnectionState, IceGatheringState, RtcError,
    SessionDescription, SignalingState, Stats,
};
use crate::senders::RtpSender;
use std::fmt::Debug;
use std::sync::Arc;
use webrtc::peer_connection::RTCPeerConnection;
use webrtc::rtp_transceiver::rtp_transceiver_direction::RTCRtpTransceiverDirection;

#[derive(uniffi::Object)]
pub struct PeerConnection {
    pub(crate) inner: RTCPeerConnection,
}

impl PeerConnection {
    pub fn new(inner: RTCPeerConnection) -> Self {
        Self { inner }
    }
}

#[uniffi::export(with_foreign)]
pub trait PeerConnectionObserver: Send + Sync + Debug {
    fn on_connection_state_change(&self, state: ConnectionState);
    fn on_ice_connection_state_change(&self, state: IceConnectionState);
    fn on_ice_gathering_state_change(&self, state: IceGatheringState);
    fn on_signaling_state_change(&self, state: SignalingState);
    fn on_ice_candidate(&self, candidate: IceCandidate);
    fn on_data_channel(&self, channel: Arc<DataChannel>);
    fn on_track(&self, track: Arc<MediaStreamTrack>);
    fn on_remove_track(&self, track: Arc<MediaStreamTrack>);
    fn on_negotiation_needed(&self);
}

#[uniffi::export(async_runtime = "tokio")]
impl PeerConnection {
    pub async fn get_local_description(&self) -> Option<SessionDescription> {
        self.inner
            .local_description()
            .await
            .map(SessionDescription::from_native)
    }

    pub async fn get_remote_description(&self) -> Option<SessionDescription> {
        self.inner
            .remote_description()
            .await
            .map(SessionDescription::from_native)
    }

    pub async fn get_statistics(&self) -> Result<Vec<Stats>, RtcError> {
        self.inner
            .get_stats()
            .await
            .reports
            .values()
            .map(Stats::from_native)
            .collect()
    }

    pub async fn create_offer(&self) -> Result<SessionDescription, RtcError> {
        self.inner
            .create_offer(None)
            .await
            .map(SessionDescription::from_native)
            .map_err(|e| SdpError(e.to_string()))
    }

    pub async fn create_answer(&self) -> Result<SessionDescription, RtcError> {
        self.inner
            .create_answer(None)
            .await
            .map(SessionDescription::from_native)
            .map_err(|e| SdpError(e.to_string()))
    }

    pub async fn create_data_channel(
        &self,
        label: &str,
        options: DataChannelInit,
    ) -> Result<Arc<DataChannel>, RtcError> {
        self.inner
            .create_data_channel(label, Some(options.to_native()))
            .await
            .map(|channel| Arc::new(DataChannel::new(channel)))
            .map_err(|e| DataChannelError(e.to_string()))
    }

    pub async fn set_local_description(
        &self,
        description: SessionDescription,
    ) -> Result<(), RtcError> {
        let local = description.to_native()?;
        self.inner
            .set_local_description(local)
            .await
            .map_err(|e| SdpError(e.to_string()))
    }

    pub async fn set_remote_description(
        &self,
        description: SessionDescription,
    ) -> Result<(), RtcError> {
        let remote = description.to_native()?;
        self.inner
            .set_remote_description(remote)
            .await
            .map_err(|e| SdpError(e.to_string()))
    }

    pub async fn add_ice_candidate(&self, candidate: IceCandidate) -> Result<(), RtcError> {
        self.inner
            .add_ice_candidate(candidate.to_native())
            .await
            .map_err(|e| IceError(e.to_string()))
    }

    pub async fn add_track(&self, track: Arc<MediaStreamTrack>) -> Result<RtpSender, RtcError> {
        let inner_track = track.as_local()?;
        // Use the peer connection add_track to create a sender/transceiver pair
        let sender = self
            .inner
            .add_track(inner_track)
            .await
            .map_err(|e| AddTrackError(e.to_string()))?;
        Ok(RtpSender::new(sender, track))
    }

    pub async fn remove_track(&self, track: Arc<MediaStreamTrack>) -> Result<(), RtcError> {
        let senders = self.inner.get_senders().await;
        for sender in senders {
            if let Some(sender_track) = sender.track().await {
                if sender_track.id() != track.id() {
                    continue;
                }
                return self
                    .inner
                    .remove_track(&sender)
                    .await
                    .map_err(|e| RemoveTrackError(e.to_string()));
            }
        }
        Err(RemoveTrackError("Track not found".to_string()))
    }

    pub async fn remove_track_by_sender(&self, sender: Arc<RtpSender>) -> Result<(), RtcError> {
        self.inner
            .remove_track(&sender.inner)
            .await
            .map_err(|e| RemoveTrackError(e.to_string()))
    }

    pub async fn restart_ice(&self) -> Result<(), RtcError> {
        self.inner
            .restart_ice()
            .await
            .map_err(|e| IceError(e.to_string()))
    }

    pub fn register_observer(self: &Self, observer_ref: &Arc<dyn PeerConnectionObserver>) {
        let observer = Arc::clone(&observer_ref);
        self.inner
            .on_peer_connection_state_change(Box::new(move |new_state| {
                observer.on_connection_state_change(ConnectionState::from_native(new_state));
                Box::pin(async {})
            }));

        let observer = Arc::clone(&observer_ref);
        self.inner
            .on_ice_connection_state_change(Box::new(move |new_state| {
                observer.on_ice_connection_state_change(IceConnectionState::from_native(new_state));
                Box::pin(async {})
            }));

        let observer = Arc::clone(&observer_ref);
        self.inner
            .on_signaling_state_change(Box::new(move |new_state| {
                observer.on_signaling_state_change(SignalingState::from_native(new_state));
                Box::pin(async move {})
            }));

        let observer = Arc::clone(&observer_ref);
        self.inner
            .on_ice_gathering_state_change(Box::new(move |new_state| {
                observer.on_ice_gathering_state_change(IceGatheringState::from_native(new_state));
                Box::pin(async {})
            }));

        let observer = Arc::clone(&observer_ref);
        self.inner.on_ice_candidate(Box::new(move |candidate| {
            if let Some(c) = candidate {
                observer.on_ice_candidate(IceCandidate::from_native(c));
            }
            Box::pin(async {})
        }));

        let observer = Arc::clone(&observer_ref);
        self.inner.on_negotiation_needed(Box::new(move || {
            observer.on_negotiation_needed();
            Box::pin(async {})
        }));

        let observer = Arc::clone(&observer_ref);
        self.inner.on_data_channel(Box::new(move |channel| {
            observer.on_data_channel(Arc::new(DataChannel { inner: channel }));
            Box::pin(async {})
        }));

        let observer = Arc::clone(&observer_ref);
        self.inner
            .on_track(Box::new(move |remote_track, _, transceiver| {
                let track = Arc::new(MediaStreamTrack::from_remote(remote_track.clone()));
                let track_ref = Arc::clone(&track);
                let observer_ref = Arc::clone(&observer);

                remote_track.onmute(move || {
                    // We do not fire onmute events for now, but add a handler here to
                    // listen for the track direction change, which is not very clear.
                    // But checking track states right after renegotiation has asynchronous issues
                    // because the track state is not updated immediately.
                    let can_receive = match transceiver.current_direction() {
                        RTCRtpTransceiverDirection::Sendonly => false,
                        RTCRtpTransceiverDirection::Inactive => false,
                        _ => true,
                    };
                    if !can_receive {
                        observer_ref.on_remove_track(track_ref.clone());
                    }
                    Box::pin(async {})
                });

                observer.on_track(track);
                Box::pin(async {})
            }))
    }
}
