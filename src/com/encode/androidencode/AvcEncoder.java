package com.encode.androidencode;

import java.nio.ByteBuffer;
import java.util.Date;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

public class AvcEncoder {
	private MediaCodec mediaCodec;
	int m_width;
	int m_height;
	// int m_format;
	byte[] m_info = null;
	private int format;
	private long presentationTimeUs;
	private BufferInfo bufferInfo;

	// byte[] m_yv12 = null;
	public int GetW() {
		return m_width;
	}

	public int GetH() {
		return m_height;
	}

	public int GetColor() {
		return format;
	}

	// public void SetFormat(int _format){
	// m_format = _format;
	// }
	@SuppressLint("NewApi")
	private static MediaCodecInfo selectCodec(String mimeType) {
		int numCodecs = MediaCodecList.getCodecCount();
		for (int i = 0; i < numCodecs; i++) {
			MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

			if (!codecInfo.isEncoder()) {
				continue;
			}

			String[] types = codecInfo.getSupportedTypes();
			for (int j = 0; j < types.length; j++) {
				if (types[j].equalsIgnoreCase(mimeType)) {
					return codecInfo;
				}
			}
		}
		return null;
	}
	
	/**
	 * Returns a color format that is supported by the codec and by this test
	 * code. If no match is found, this throws a test failure -- the set of
	 * formats known to the test should be expanded for new platforms.
	 */
	@SuppressLint("NewApi")
	private static int selectColorFormat(MediaCodecInfo codecInfo,
			String mimeType) {
		MediaCodecInfo.CodecCapabilities capabilities = codecInfo
				.getCapabilitiesForType(mimeType);
		for (int i = 0; i < capabilities.colorFormats.length; i++) {
			int colorFormat = capabilities.colorFormats[i];
			if (isRecognizedFormat(colorFormat)) {
				Log.v("h264", "colorformat = " + colorFormat + "");
				return colorFormat;
			}
		}
		Log.e("h264",
				"couldn't find a good color format for " + codecInfo.getName()
						+ " / " + mimeType);
		return 0; // not reached
	}

	/**
	 * Returns true if this is a color format that this test code understands
	 * (i.e. we know how to read and generate frames in this format).
	 */
	private static boolean isRecognizedFormat(int colorFormat) {
		switch (colorFormat) {
		// these are the formats we know how to handle for this testcase
		// MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
		case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
		case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
		case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
		case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
		case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
			// case MediaCodecInfo.CodecCapabilities.
			return true;
		default:
			return false;
		}
	}

	@SuppressLint("NewApi")
	public AvcEncoder(int width, int height, int framerate, int bitrate) {

		m_width = width;
		m_height = height;
		// m_format = ImageFormat.NV21;
		String mime = "video/avc";
		// m_yv12 = new byte[width * height * 3 / 2];
		format = selectColorFormat(selectCodec(mime), mime);
		mediaCodec = MediaCodec.createEncoderByType(mime);
		MediaFormat mediaFormat = MediaFormat.createVideoFormat(mime, width,
				height);
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
		// mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
		if (format > 0)
			mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, format);
		else
			mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
					MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
		try {
			mediaCodec.configure(mediaFormat, null, null,
					MediaCodec.CONFIGURE_FLAG_ENCODE);
		} catch (Exception ee) {
			ee.printStackTrace();
		}
		bufferInfo = new MediaCodec.BufferInfo();
		presentationTimeUs = new Date().getTime();
		mediaCodec.start();
	}

	@SuppressLint("NewApi")
	public void close() {
		try {
			mediaCodec.stop();
			mediaCodec.release();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@SuppressLint("NewApi")
	public int offerEncoder(byte[] input, byte[] output) {
		int pos = 0;
		try {
			ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
			ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
			int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
			if (inputBufferIndex >= 0) {
				ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
				inputBuffer.clear();
				inputBuffer.put(input);
				long pts = new Date().getTime() - presentationTimeUs;
				mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, pts, 0);
			}
			ByteBuffer outputBuffer = null;
			for (;;) {
				int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
				if (outputBufferIndex >= 0) {
					outputBuffer = outputBuffers[outputBufferIndex];
					outputBuffer.get(output,0, bufferInfo.size);
					pos += bufferInfo.size;
					mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
					mediaCodec.flush();
				} else {
					break;
				}
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}

		return pos;
	}
}
