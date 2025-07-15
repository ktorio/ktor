/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
mod sink;
mod track;

use arc_swap::ArcSwap;
use std::fmt::Debug;
use std::sync::Arc;
use std::sync::atomic::AtomicBool;
use std::time::{Duration, SystemTime};
use tokio::sync::Mutex;
use webrtc::track::track_local::track_local_static_sample::TrackLocalStaticSample;
use webrtc::track::track_remote::TrackRemote;

#[derive(uniffi::Enum, Clone, Debug)]
pub enum MediaKind {
    Audio,
    Video,
}

#[derive(uniffi::Object)]
pub struct MediaStreamTrack {
    id: String,
    kind: MediaKind,
    enabled: AtomicBool,
    inner: MediaStreamTrackInner,
    sink: ArcSwap<MediaStreamSink>,
}

#[derive(uniffi::Record)]
pub struct TrackSample {
    pub data: Vec<u8>,
    pub timestamp: SystemTime,
    pub duration: Duration,
    pub packet_timestamp: u32,
    pub prev_dropped_packets: u16,
    pub prev_padding_packets: u16,
}

#[derive(uniffi::Object)]
pub struct MediaStreamSink {
    writer: Option<Arc<Mutex<dyn webrtc::media::io::Writer + Send + Sync>>>,
}

#[uniffi::export(with_foreign)]
pub trait MediaSinkHandler: Send + Sync + Debug {
    /// Called when parsed media data is available
    fn on_media_data(&self, data: Vec<u8>);
    /// Called when the sink is closed
    fn on_close(&self);
}

impl MediaStreamSink {
    pub fn empty() -> Self {
        Self { writer: None }
    }

    pub fn new(writer: Arc<Mutex<dyn webrtc::media::io::Writer + Send + Sync>>) -> Self {
        Self {
            writer: Some(writer),
        }
    }
}

enum MediaStreamTrackInner {
    LocalSample(Arc<TrackLocalStaticSample>),
    Remote(Arc<TrackRemote>),
}

#[derive(uniffi::Enum, Debug)]
pub enum CodecMimeType {
    VideoVP8,
    VideoH264,
    AudioOpus,
}
