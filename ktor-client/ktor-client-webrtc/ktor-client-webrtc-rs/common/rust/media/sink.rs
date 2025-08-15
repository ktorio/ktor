/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

use crate::media::{MediaSinkHandler, MediaStreamSink, MediaStreamTrack};
use crate::rtc::RtcError;
use crate::rtc::RtcError::MediaTrackError;
use std::io::{Seek, SeekFrom, Write};
use std::sync::Arc;
use tokio::sync::Mutex;
use webrtc::media::io::h264_writer::H264Writer;
use webrtc::media::io::ivf_reader::{IVF_FILE_HEADER_SIZE, IVFFileHeader};
use webrtc::media::io::ivf_writer::IVFWriter;
use webrtc::media::io::ogg_writer::OggWriter;

/// Custom writer that captures data and forwards to the handler
pub struct InMemoryWriter {
    handler: Arc<dyn MediaSinkHandler>,
    position: u64, // Track virtual position for Seek implementation
}

impl InMemoryWriter {
    pub fn new(handler: Arc<dyn MediaSinkHandler>) -> Self {
        Self {
            handler,
            position: 0,
        }
    }
}

impl Write for InMemoryWriter {
    fn write(&mut self, buf: &[u8]) -> std::io::Result<usize> {
        // Just send immediately, no buffering
        self.handler.on_media_data(buf.to_vec());
        self.position += buf.len() as u64;
        Ok(buf.len())
    }

    fn flush(&mut self) -> std::io::Result<()> {
        // Nothing to flush since we don't buffer
        Ok(())
    }
}

impl Seek for InMemoryWriter {
    fn seek(&mut self, pos: SeekFrom) -> std::io::Result<u64> {
        // For in-memory writer, seeking makes little sense,
        // but we need to implement it for compatibility
        let new_pos = match pos {
            SeekFrom::Start(pos) => pos,
            SeekFrom::End(_) => {
                // We don't know the "end" for a streaming writer
                return Err(std::io::Error::new(
                    std::io::ErrorKind::Unsupported,
                    "SeekFrom::End not supported for streaming writer",
                ));
            }
            SeekFrom::Current(offset) => {
                if offset < 0 && self.position < (-offset) as u64 {
                    return Err(std::io::Error::new(
                        std::io::ErrorKind::InvalidInput,
                        "Attempted to seek before start of stream",
                    ));
                }
                if offset < 0 {
                    self.position - (-offset) as u64
                } else {
                    self.position + offset as u64
                }
            }
        };

        self.position = new_pos;
        Ok(self.position)
    }

    fn rewind(&mut self) -> std::io::Result<()> {
        self.position = 0;
        Ok(())
    }

    fn stream_position(&mut self) -> std::io::Result<u64> {
        Ok(self.position)
    }

    fn seek_relative(&mut self, offset: i64) -> std::io::Result<()> {
        self.seek(SeekFrom::Current(offset))?;
        Ok(())
    }
}

impl Drop for InMemoryWriter {
    fn drop(&mut self) {
        self.handler.on_close()
    }
}

#[uniffi::export]
impl MediaStreamTrack {
    pub fn create_sink(
        &self,
        handler: Arc<dyn MediaSinkHandler>,
    ) -> Result<Arc<MediaStreamSink>, RtcError> {
        let track = self.as_remote()?;
        match track.payload_type() {
            96 => self.create_vp8_video_sink(handler),
            111 => self.create_opus_audio_sink(handler),
            102 | 108 | 125 | 123 | 127 => Ok(self.create_h264_video_sink(handler)),
            p_type => Err(MediaTrackError(format!(
                "Unsupported payload type: {}",
                p_type
            ))),
        }
    }

    pub fn create_h264_video_sink(
        &self,
        handler: Arc<dyn MediaSinkHandler>,
    ) -> Arc<MediaStreamSink> {
        let writer_impl = InMemoryWriter::new(handler.clone());
        let h264_writer = H264Writer::new(writer_impl);
        let writer = Arc::new(Mutex::new(h264_writer));
        Arc::new(MediaStreamSink::new(writer))
    }

    pub fn create_vp8_video_sink(
        &self,
        handler: Arc<dyn MediaSinkHandler>,
    ) -> Result<Arc<MediaStreamSink>, RtcError> {
        // Create IVF header for VP8
        let header = IVFFileHeader {
            signature: *b"DKIF",
            version: 0,
            header_size: IVF_FILE_HEADER_SIZE as u16,
            four_cc: *b"VP80", // VP8 FourCC
            width: 640,
            height: 480,
            timebase_denominator: 30, // 30 FPS default
            timebase_numerator: 1,
            num_frames: 0, // Unknown for streaming
            unused: 0,
        };

        let writer_impl = InMemoryWriter::new(handler.clone());
        let ivf_writer =
            IVFWriter::new(writer_impl, &header).map_err(|e| MediaTrackError(e.to_string()))?;
        let writer = Arc::new(Mutex::new(ivf_writer));
        Ok(Arc::new(MediaStreamSink::new(writer)))
    }

    pub fn create_opus_audio_sink(
        &self,
        handler: Arc<dyn MediaSinkHandler>,
    ) -> Result<Arc<MediaStreamSink>, RtcError> {
        let writer_impl = InMemoryWriter::new(handler.clone());
        let ogg_writer =
            OggWriter::new(writer_impl, 48000, 2).map_err(|e| MediaTrackError(e.to_string()))?;
        let writer = Arc::new(Mutex::new(ogg_writer));
        Ok(Arc::new(MediaStreamSink::new(writer)))
    }
}
