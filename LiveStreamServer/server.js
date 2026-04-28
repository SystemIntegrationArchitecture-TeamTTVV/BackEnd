// ── TTVV Live Stream — RTMP → HLS Media Server ──────────────────────────
// Receives RTMP from OBS, converts to HLS for browser playback.
// Notifies MessageService via webhooks on stream start/end.

const NodeMediaServer = require('node-media-server');
const axios = require('axios');

const MESSAGE_SERVICE_URL = process.env.MESSAGE_SERVICE_URL || 'http://localhost:8082';

const config = {
  rtmp: {
    port: 1935,
    chunk_size: 60000,
    gop_cache: true,
    ping: 30,
    ping_timeout: 60,
  },
  http: {
    port: 8888,
    allow_origin: '*',
    mediaroot: './media',
  },
  trans: {
    ffmpeg: process.env.FFMPEG_PATH || '/usr/bin/ffmpeg',
    tasks: [
      {
        app: 'live',
        hls: true,
        hlsFlags: '[hls_time=2:hls_list_size=3:hls_flags=delete_segments]',
        hlsKeep: false,
        dash: false,
      },
    ],
  },
};

const nms = new NodeMediaServer(config);

// ── Lifecycle Hooks ──────────────────────────────────────────────────────

// When OBS starts pushing to rtmp://server:1935/live/<streamKey>
nms.on('prePublish', async (id, StreamPath, args) => {
  const streamKey = StreamPath.split('/')[2];
  console.log(`🔴 [RTMP] Stream STARTED: key=${streamKey}, path=${StreamPath}`);

  try {
    await axios.post(`${MESSAGE_SERVICE_URL}/livestream/hook/start`, {
      streamKey,
    });
    console.log(`✅ [Hook] Notified backend: stream started`);
  } catch (err) {
    console.error(`❌ [Hook] Failed to notify start:`, err.message);
  }
});

// When OBS stops streaming
nms.on('donePublish', async (id, StreamPath, args) => {
  const streamKey = StreamPath.split('/')[2];
  console.log(`⬛ [RTMP] Stream ENDED: key=${streamKey}`);

  try {
    await axios.post(`${MESSAGE_SERVICE_URL}/livestream/hook/end`, {
      streamKey,
    });
    console.log(`✅ [Hook] Notified backend: stream ended`);
  } catch (err) {
    console.error(`❌ [Hook] Failed to notify end:`, err.message);
  }
});

// When a viewer connects
nms.on('prePlay', (id, StreamPath, args) => {
  console.log(`👁️ [RTMP] Viewer connected: path=${StreamPath}`);
});

// When a viewer disconnects
nms.on('donePlay', (id, StreamPath, args) => {
  console.log(`👁️ [RTMP] Viewer disconnected: path=${StreamPath}`);
});

// ── Start server ──────────────────────────────────────────────────────────
nms.run();

console.log(`
╔══════════════════════════════════════════════════╗
║          🎥 TTVV Live Stream Server              ║
╠══════════════════════════════════════════════════╣
║  RTMP:  rtmp://localhost:1935/live/<stream-key>  ║
║  HLS:   http://localhost:8888/live/<key>/index.m3u8 ║
║  Backend: ${MESSAGE_SERVICE_URL}                  ║
╚══════════════════════════════════════════════════╝
`);
