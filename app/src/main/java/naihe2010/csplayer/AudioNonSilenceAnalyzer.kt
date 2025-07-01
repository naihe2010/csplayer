package naihe2010.csplayer

import android.media.*
import java.io.IOException
import java.nio.ByteBuffer

class AudioNonSilenceAnalyzer {

    data class Segment(
        val startMs: Long,
        val endMs: Long
    ) {
        val durationMs: Long
            get() = endMs - startMs
    }

    companion object {
        // 默认参数值
        private const val DEFAULT_SILENCE_THRESHOLD_DB = -50.0
        private const val DEFAULT_MIN_SILENCE_DURATION_MS = 2000L

        /**
         * 分析音频文件并返回非静音片段
         *
         * @param filePath 音频文件路径
         * @param silenceThresholdDb 静音阈值（分贝），低于此值视为静音
         * @param minSilenceDurationMs 最小静音持续时间（毫秒），短于此值不视为静音
         * @return 非静音片段列表（按时间顺序排序）
         */
        @Throws(IOException::class)
        fun detectNonSilenceSegments(
            filePath: String,
            silenceThresholdDb: Double = DEFAULT_SILENCE_THRESHOLD_DB,
            minSilenceDurationMs: Long = DEFAULT_MIN_SILENCE_DURATION_MS
        ): List<Segment> {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            val nonSilenceSegments = mutableListOf<Segment>()

            try {
                // 1. 设置音频文件源
                extractor.setDataSource(filePath)

                // 2. 选择音频轨道
                val audioTrackIndex = (0 until extractor.trackCount)
                    .firstOrNull { i ->
                        extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                            ?.startsWith("audio/") == true
                    } ?: throw IOException("No audio track found")

                extractor.selectTrack(audioTrackIndex)
                val format = extractor.getTrackFormat(audioTrackIndex)
                val durationUs = format.getLong(MediaFormat.KEY_DURATION)

                // 3. 创建解码器
                val mimeType = format.getString(MediaFormat.KEY_MIME)
                    ?: throw IOException("Invalid audio format")

                codec = MediaCodec.createDecoderByType(mimeType).apply {
                    configure(format, null, null, 0)
                    start()
                }

                // 4. 音频处理变量
                var currentNonSilenceStartUs: Long = 0 // 当前非静音段开始时间（微秒）
                var isInNonSilence = false             // 是否处于非静音状态

                // 5. 解码循环
                val info = MediaCodec.BufferInfo()
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                var sawInputEOS = false
                var sawOutputEOS = false

                while (!sawOutputEOS) {
                    // 输入缓冲区处理
                    if (!sawInputEOS) {
                        val inputBufferIndex = codec.dequeueInputBuffer(10000)
                        if (inputBufferIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                            val sampleSize = inputBuffer?.let {
                                extractor.readSampleData(it, 0)
                            } ?: 0

                            if (sampleSize < 0) {
                                sawInputEOS = true
                                codec.queueInputBuffer(
                                    inputBufferIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                codec.queueInputBuffer(
                                    inputBufferIndex, 0, sampleSize,
                                    presentationTimeUs, 0
                                )
                                extractor.advance()
                            }
                        }
                    }

                    // 输出缓冲区处理
                    val outputBufferIndex = codec.dequeueOutputBuffer(info, 10000)
                    when {
                        outputBufferIndex >= 0 -> {
                            val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                            if (outputBuffer != null && info.size > 0) {
                                // 6. 静音检测
                                val positionUs = info.presentationTimeUs
                                val isSilent = isBufferSilent(
                                    buffer = outputBuffer,
                                    offset = info.offset,
                                    size = info.size,
                                    silenceThresholdDb = silenceThresholdDb
                                )

                                if (!isSilent) {
                                    // 检测到声音
                                    if (!isInNonSilence) {
                                        // 开始新的非静音段
                                        currentNonSilenceStartUs = positionUs
                                        isInNonSilence = true
                                    }
                                } else {
                                    // 检测到静音
                                    if (isInNonSilence) {
                                        // 结束当前非静音段
                                        val segment = Segment(
                                            startMs = currentNonSilenceStartUs / 1000,
                                            endMs = positionUs / 1000
                                        )
                                        nonSilenceSegments.add(segment)
                                        isInNonSilence = false
                                    }
                                }
                            }
                            codec.releaseOutputBuffer(outputBufferIndex, false)
                        }

                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED == outputBufferIndex -> {
                            // 格式变化处理
                            codec.outputFormat
                        }

                        MediaCodec.INFO_TRY_AGAIN_LATER == outputBufferIndex -> {
                            // 稍后重试
                        }
                    }

                    // 结束条件
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEOS = true
                    }
                }

                // 7. 处理文件末尾的非静音段
                if (isInNonSilence) {
                    val segment = Segment(
                        startMs = currentNonSilenceStartUs / 1000,
                        endMs = durationUs / 1000
                    )
                    nonSilenceSegments.add(segment)
                }

                // 8. 处理文件开头可能存在的非静音段（如果第一个片段是静音）
                if (nonSilenceSegments.isEmpty() && durationUs > 0) {
                    nonSilenceSegments.add(Segment(0, durationUs / 1000))
                }

            } finally {
                // 9. 释放资源
                codec?.stop()
                codec?.release()
                extractor.release()
            }

            // 10. 过滤过短的静音间隔
            return mergeShortSilenceGaps(
                segments = nonSilenceSegments,
                minSilenceDurationMs = minSilenceDurationMs,
            )
        }

        // 检测缓冲区是否静音
        private fun isBufferSilent(
            buffer: ByteBuffer,
            offset: Int,
            size: Int,
            silenceThresholdDb: Double
        ): Boolean {
            if (size == 0) return true

            // 创建临时缓冲区副本以避免修改原始位置
            val tempBuffer = buffer.duplicate().apply {
                position(offset)
                limit(offset + size)
            }

            // 转换为short数组（16位PCM）
            val sampleBytes = size / 2
            val samples = ShortArray(sampleBytes)
            tempBuffer.asShortBuffer().get(samples)

            // 计算RMS（均方根）
            var sumSquares = 0.0
            for (sample in samples) {
                val normalized = sample / 32768.0 // 归一化到[-1, 1]
                sumSquares += normalized * normalized
            }

            val rms = if (samples.isNotEmpty()) {
                Math.sqrt(sumSquares / samples.size)
            } else {
                0.0
            }

            // 转换为分贝（避免log10(0)）
            val db = 20 * Math.log10(rms + Double.MIN_VALUE)

            return db < silenceThresholdDb
        }

        /**
         * 合并过短的静音间隔
         *
         * 当两个非静音段之间的静音间隔小于阈值时，将它们合并为一个非静音段
         */
        private fun mergeShortSilenceGaps(
            segments: List<Segment>,
            minSilenceDurationMs: Long,
        ): List<Segment> {
            if (segments.size <= 1) return segments

            val mergedSegments = mutableListOf<Segment>()
            var currentSegment = segments.first()

            for (i in 1 until segments.size) {
                val nextSegment = segments[i]
                val gapDuration = nextSegment.startMs - currentSegment.endMs

                if (gapDuration < minSilenceDurationMs) {
                    // 静音间隔过短，合并非静音段
                    currentSegment = Segment(
                        startMs = currentSegment.startMs,
                        endMs = nextSegment.endMs
                    )
                } else {
                    // 静音间隔足够长，保留当前段
                    mergedSegments.add(currentSegment)
                    currentSegment = nextSegment
                }
            }

            // 添加最后一个段
            mergedSegments.add(currentSegment)

            return mergedSegments
        }
    }
}
