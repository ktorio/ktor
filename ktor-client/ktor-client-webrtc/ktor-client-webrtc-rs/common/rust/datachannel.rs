/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
use crate::rtc::RtcError;
use crate::rtc::RtcError::DataChannelError;
use std::fmt::Debug;
use std::sync::Arc;
use uniffi::deps::bytes::Bytes;
use webrtc::data_channel::RTCDataChannel;
use webrtc::data_channel::data_channel_init::RTCDataChannelInit;
use webrtc::data_channel::data_channel_state::RTCDataChannelState;

#[derive(uniffi::Enum)]
pub enum DataChannelState {
    Connecting,
    Open,
    Closing,
    Closed,
}

#[derive(uniffi::Object)]
pub struct DataChannel {
    pub inner: Arc<RTCDataChannel>,
}

impl DataChannel {
    pub fn new(inner: Arc<RTCDataChannel>) -> Self {
        Self { inner }
    }
}

#[derive(uniffi::Record)]
pub struct DataChannelMessage {
    pub is_string: bool,
    pub data: Vec<u8>,
}

impl DataChannelMessage {
    fn from_native(
        message: webrtc::data_channel::data_channel_message::DataChannelMessage,
    ) -> Self {
        Self {
            is_string: message.is_string,
            data: message.data.to_vec(),
        }
    }
}

#[derive(uniffi::Record)]
pub struct DataChannelInit {
    pub ordered: Option<bool>,
    pub max_retransmits: Option<u16>,
    pub max_packet_life_time: Option<u16>,
    pub protocol: Option<String>,
    pub negotiated: Option<u16>,
}

impl DataChannelInit {
    pub fn to_native(self) -> RTCDataChannelInit {
        RTCDataChannelInit {
            ordered: self.ordered,
            protocol: self.protocol,
            negotiated: self.negotiated,
            max_retransmits: self.max_retransmits,
            max_packet_life_time: self.max_packet_life_time,
        }
    }
}

#[uniffi::export(with_foreign)]
pub trait DataChannelObserver: Send + Sync + Debug {
    fn on_open(&self);
    fn on_close(&self);
    fn on_buffered_amount_low(&self);
    fn on_error(&self, error: RtcError);
    fn on_message(&self, message: DataChannelMessage);
}

#[uniffi::export(async_runtime = "tokio")]
impl DataChannel {
    pub fn id(&self) -> u16 {
        self.inner.id()
    }

    pub fn label(&self) -> String {
        self.inner.label().to_string()
    }

    pub fn ordered(&self) -> bool {
        self.inner.ordered()
    }

    pub fn max_packet_lifetime(&self) -> Option<u16> {
        self.inner.max_packet_lifetime()
    }

    pub fn max_retransmits(&self) -> Option<u16> {
        self.inner.max_retransmits()
    }

    pub fn protocol(&self) -> String {
        self.inner.protocol().to_string()
    }

    pub fn state(&self) -> DataChannelState {
        match self.inner.ready_state() {
            RTCDataChannelState::Unspecified => unreachable!(),
            RTCDataChannelState::Connecting => DataChannelState::Connecting,
            RTCDataChannelState::Open => DataChannelState::Open,
            RTCDataChannelState::Closing => DataChannelState::Closing,
            RTCDataChannelState::Closed => DataChannelState::Closed,
        }
    }

    pub fn negotiated(&self) -> bool {
        self.inner.negotiated()
    }

    pub async fn buffered_amount(&self) -> u64 {
        self.inner.buffered_amount().await as u64
    }

    pub async fn buffered_amount_low_threshold(&self) -> u64 {
        self.inner.buffered_amount_low_threshold().await as u64
    }

    pub async fn set_buffered_amount_low_threshold(&self, threshold: u64) {
        self.inner
            .set_buffered_amount_low_threshold(threshold as usize)
            .await
    }

    pub async fn send(&self, text: Vec<u8>) -> Result<u64, RtcError> {
        self.inner
            .send(&Bytes::from(text))
            .await
            .map(|bytes_sent| bytes_sent as u64)
            .map_err(|e| DataChannelError(e.to_string()))
    }

    pub async fn send_text(&self, text: &String) -> Result<u64, RtcError> {
        self.inner
            .send_text(text)
            .await
            .map(|bytes_sent| bytes_sent as u64)
            .map_err(|e| DataChannelError(e.to_string()))
    }

    // call it `close_channel` instead of `close` to avoid conflict with `AutoClosable`
    pub async fn close_channel(&self) -> Result<(), RtcError> {
        self.inner
            .close()
            .await
            .map_err(|e| DataChannelError(e.to_string()))
    }

    pub async fn register_observer(&self, observer_ref: &Arc<dyn DataChannelObserver>) {
        let observer = observer_ref.clone();
        self.inner.on_message(Box::new(move |m| {
            observer.on_message(DataChannelMessage::from_native(m));
            Box::pin(async {})
        }));

        let observer = observer_ref.clone();
        self.inner
            .on_buffered_amount_low(Box::new(move || {
                observer.on_buffered_amount_low();
                Box::pin(async {})
            }))
            .await;

        let observer = observer_ref.clone();
        self.inner.on_open(Box::new(move || {
            observer.on_open();
            Box::pin(async {})
        }));

        let observer = observer_ref.clone();
        self.inner.on_close(Box::new(move || {
            observer.on_close();
            Box::pin(async {})
        }));

        let observer = observer_ref.clone();
        self.inner.on_error(Box::new(move |e| {
            observer.on_error(DataChannelError(e.to_string()));
            Box::pin(async {})
        }));
    }
}
