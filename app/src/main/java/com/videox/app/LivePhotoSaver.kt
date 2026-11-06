package com.videox.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// 把动图的静态图和视频部分合成为一张“动态照片”：
// JPEG 图片末尾内嵌 MP4，并写入三套识别标记——
// 1）Google Motion Photo 标准 XMP（GCamera + Container），谷歌相册等认；
// 2）OPPO/一加 ColorOS 扩展：XMP 的 OpCamera 字段 + EXIF UserComment 暗号
//    “oplus_10485792” + MPF 段（2026-11-06 拆解一加 13T 原厂动态照片样本
//    得出的配方，样本与说明在 ~/workspace/videox/samples/）；
// 保存成功只代表文件已生成并存入相册，是否显示为动态照片以相册实际效果为准。
suspend fun saveMotionPhoto(
    context: Context,
    imageUrl: String,
    videoUrl: String,
    title: String
): String {

    return withContext(Dispatchers.IO) {

        try {

            val imageBytes = httpGetBytes(imageUrl)
                ?: throw Exception("静态图下载失败")

            val videoBytes = httpGetBytes(videoUrl)
                ?: throw Exception("动图视频下载失败")

            val bitmap = BitmapFactory.decodeByteArray(
                imageBytes,
                0,
                imageBytes.size
            ) ?: throw Exception("静态图解码失败")

            val jpegOut = ByteArrayOutputStream()

            bitmap.compress(
                Bitmap.CompressFormat.JPEG,
                92,
                jpegOut
            )

            val fused = buildMotionPhotoJpeg(
                jpegOut.toByteArray(),
                videoBytes
            )

            val fileName =
                "VideoX_${System.currentTimeMillis()}.jpg"

            if (Build.VERSION.SDK_INT >= 29) {

                val values = ContentValues().apply {

                    put(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        fileName
                    )

                    put(
                        MediaStore.Images.Media.MIME_TYPE,
                        "image/jpeg"
                    )

                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/VideoX"
                    )
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                ) ?: throw Exception("无法在相册中创建文件")

                val stream = context.contentResolver
                    .openOutputStream(uri)
                    ?: throw Exception("无法写入相册文件")

                stream.use { it.write(fused) }

            } else {

                val dir = File(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES
                    ),
                    "VideoX"
                )

                dir.mkdirs()

                val file = File(dir, fileName)

                file.writeBytes(fused)

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )
            }

            "动态照片已保存到相册（Pictures/VideoX），请在相册里确认是否会动"

        } catch (e: Exception) {

            "动态照片保存失败：${e.message ?: "未知错误"}"
        }
    }
}

// 带请求头下载一个文件为字节数组（与解析、下载用的请求头一致）
private fun httpGetBytes(urlText: String): ByteArray? {

    val connection = URL(urlText)
        .openConnection() as HttpURLConnection

    return try {

        connection.requestMethod = "GET"
        connection.connectTimeout = 20000
        connection.readTimeout = 30000

        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
        )

        connection.setRequestProperty(
            "Referer",
            "https://www.douyin.com/"
        )

        if (connection.responseCode in 200..299) {
            connection.inputStream?.use { it.readBytes() }
        } else {
            null
        }

    } finally {

        connection.disconnect()
    }
}

// 在 JPEG 的 SOI 之后依次插入 EXIF APP1、XMP APP1、MPF APP2 三段，末尾拼接 MP4
private fun buildMotionPhotoJpeg(
    jpeg: ByteArray,
    video: ByteArray
): ByteArray {

    // 展示时间戳取视频时长的一半（解析 MP4 的 mvhd），取不到时用 0.5 秒
    val timestampUs = motionTimestampUs(video)

    // OPPO 的 VideoLength 是“从 MP4 开头数到 mdat 盒结尾”的字节数，
    // 与文件总长不同（原厂文件在 mdat 后还挂了私有尾盒）
    val oppoVideoLength = mp4MdatEndLength(video)

    val xmp = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" x:xmptk=\"VideoX\">" +
            "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">" +
            "<rdf:Description rdf:about=\"\"" +
            " xmlns:GCamera=\"http://ns.google.com/photos/1.0/camera/\"" +
            " xmlns:OpCamera=\"http://ns.oplus.com/photos/1.0/camera/\"" +
            " xmlns:Container=\"http://ns.google.com/photos/1.0/container/\"" +
            " xmlns:Item=\"http://ns.google.com/photos/1.0/container/item/\"" +
            " GCamera:MotionPhoto=\"1\"" +
            " GCamera:MotionPhotoVersion=\"1\"" +
            " GCamera:MotionPhotoPresentationTimestampUs=\"" + timestampUs + "\"" +
            " OpCamera:MotionPhotoPrimaryPresentationTimestampUs=\"" + timestampUs + "\"" +
            " OpCamera:MotionPhotoOwner=\"oplus\"" +
            " OpCamera:OLivePhotoVersion=\"2\"" +
            " OpCamera:VideoLength=\"" + oppoVideoLength + "\">" +
            "<Container:Directory><rdf:Seq>" +
            "<rdf:li rdf:parseType=\"Resource\">" +
            "<Item:Mime>image/jpeg</Item:Mime>" +
            "<Item:Semantic>Primary</Item:Semantic>" +
            "<Item:Length>0</Item:Length>" +
            "<Item:Padding>0</Item:Padding>" +
            "</rdf:li>" +
            "<rdf:li rdf:parseType=\"Resource\">" +
            "<Item:Mime>video/mp4</Item:Mime>" +
            "<Item:Semantic>MotionPhoto</Item:Semantic>" +
            "<Item:Length>" + video.size + "</Item:Length>" +
            "<Item:Padding>0</Item:Padding>" +
            "</rdf:li>" +
            "</rdf:Seq></Container:Directory>" +
            "</rdf:Description></rdf:RDF></x:xmpmeta>"

    val header = "http://ns.adobe.com/xap/1.0/\u0000"
        .toByteArray(Charsets.UTF_8)

    val xmpPayload = header + xmp.toByteArray(Charsets.UTF_8)

    val exifSegment = buildExifSegment()
    val xmpSegment = appSegment(0xE1, xmpPayload)

    // MPF 段里要写主图的准确总长；三段长度都与数值无关（定宽字段），
    // 先量出总长再回填生成
    val mpfSegmentLength = 2 + 2 + 4 + 66
    val primaryLength = 2 + exifSegment.size + xmpSegment.size +
            mpfSegmentLength + (jpeg.size - 2)
    val mpfSegment = buildMpfSegment(primaryLength)

    val out = ByteArrayOutputStream()

    // JPEG 以 FFD8（SOI）开头，三段标记紧跟其后
    out.write(jpeg, 0, 2)
    out.write(exifSegment)
    out.write(xmpSegment)
    out.write(mpfSegment)
    out.write(jpeg, 2, jpeg.size - 2)
    out.write(video)

    return out.toByteArray()
}

// 给一段载荷包上 JPEG 段头：标记（FFE1/FFE2）+ 2 字节长度（含长度字段本身）
private fun appSegment(
    marker: Int,
    payload: ByteArray
): ByteArray {

    val segmentLength = payload.size + 2

    val out = ByteArrayOutputStream()

    out.write(0xFF)
    out.write(marker)
    out.write((segmentLength shr 8) and 0xFF)
    out.write(segmentLength and 0xFF)
    out.write(payload)

    return out.toByteArray()
}

// 构造 EXIF APP1 段：最小 TIFF，只含一条 ExifIFD 指针和 UserComment。
// UserComment 内容 “ASCII\0\0\0oplus_10485792”（共 22 字节）是
// ColorOS 相册识别动态照片的暗号，照一加 13T 原厂样本复刻（大端 MM）。
private fun buildExifSegment(): ByteArray {

    val comment = "ASCII\u0000\u0000\u0000oplus_10485792"
        .toByteArray(Charsets.US_ASCII)

    val tiff = ByteArrayOutputStream()

    fun put16(v: Int) {
        tiff.write((v shr 8) and 0xFF)
        tiff.write(v and 0xFF)
    }

    fun put32(v: Int) {
        tiff.write((v shr 24) and 0xFF)
        tiff.write((v shr 16) and 0xFF)
        tiff.write((v shr 8) and 0xFF)
        tiff.write(v and 0xFF)
    }

    // TIFF 头：大端 MM、魔数 42、IFD0 位于偏移 8
    tiff.write(byteArrayOf(0x4D, 0x4D))
    put16(42)
    put32(8)

    // IFD0（偏移 8）：1 条——ExifIFD 指针 0x8769，LONG，指向偏移 26
    put16(1)
    put16(0x8769)
    put16(4)
    put32(1)
    put32(26)
    put32(0)

    // ExifIFD（偏移 26）：1 条——UserComment 0x9286，UNDEFINED，
    // 22 字节放在偏移 44
    put16(1)
    put16(0x9286)
    put16(7)
    put32(comment.size)
    put32(44)
    put32(0)

    // 偏移 44：UserComment 数据
    tiff.write(comment)

    val payload = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII) +
            tiff.toByteArray()

    return appSegment(0xE1, payload)
}

// 构造 MPF APP2 段：声明文件内只有一张主图，并写主图的准确字节数。
// 结构照原厂样本（大端，MPEntry 数据区位于 TIFF 偏移 50）。
private fun buildMpfSegment(primaryLength: Int): ByteArray {

    val tiff = ByteArrayOutputStream()

    fun put16(v: Int) {
        tiff.write((v shr 8) and 0xFF)
        tiff.write(v and 0xFF)
    }

    fun put32(v: Int) {
        tiff.write((v shr 24) and 0xFF)
        tiff.write((v shr 16) and 0xFF)
        tiff.write((v shr 8) and 0xFF)
        tiff.write(v and 0xFF)
    }

    tiff.write(byteArrayOf(0x4D, 0x4D))
    put16(42)
    put32(8)

    // IFD 共 3 条
    put16(3)

    // 0xB000 版本：UNDEFINED×4，内联 "0100"
    put16(0xB000)
    put16(7)
    put32(4)
    tiff.write(byteArrayOf(0x30, 0x31, 0x30, 0x30))

    // 0xB001 图片数量：LONG×1 = 1
    put16(0xB001)
    put16(4)
    put32(1)
    put32(1)

    // 0xB002 MPEntry：UNDEFINED×16，数据在偏移 50
    put16(0xB002)
    put16(7)
    put32(16)
    put32(50)

    put32(0)

    // MPEntry（偏移 50）：属性 0、主图字节数、偏移 0、依赖图号 0/0
    put32(0)
    put32(primaryLength)
    put32(0)
    put16(0)
    put16(0)

    val payload = "MPF\u0000".toByteArray(Charsets.US_ASCII) +
            tiff.toByteArray()

    return appSegment(0xE2, payload)
}

// 从 MP4 开头逐盒扫描，返回 mdat 盒结尾的位置（= OPPO 的 VideoLength）；
// 找不到 mdat 或结构异常时回退为文件总长
private fun mp4MdatEndLength(video: ByteArray): Int {

    var offset = 0

    while (offset + 8 <= video.size) {

        var boxSize = ((video[offset].toInt() and 0xFF) shl 24) or
                ((video[offset + 1].toInt() and 0xFF) shl 16) or
                ((video[offset + 2].toInt() and 0xFF) shl 8) or
                (video[offset + 3].toInt() and 0xFF)

        var headerSize = 8

        if (boxSize == 1) {

            if (offset + 16 > video.size) {
                return video.size
            }

            var large = 0L

            for (i in 0 until 8) {
                large = (large shl 8) or
                        (video[offset + 8 + i].toInt() and 0xFF).toLong()
            }

            if (large > Int.MAX_VALUE) {
                return video.size
            }

            boxSize = large.toInt()
            headerSize = 16
        } else if (boxSize == 0) {
            boxSize = video.size - offset
        }

        if (boxSize < headerSize) {
            return video.size
        }

        val type = String(
            video,
            offset + 4,
            4,
            Charsets.US_ASCII
        )

        if (type == "mdat") {
            return offset + boxSize
        }

        offset += boxSize
    }

    return video.size
}

// 读 mvhd 的时长，返回一半时长的微秒数作为展示时间戳；解析失败回退 0.5 秒
private fun motionTimestampUs(video: ByteArray): Long {

    return try {

        var mvhdIndex = -1

        for (i in 0..video.size - 4) {

            if (video[i] == 0x6D.toByte() &&
                video[i + 1] == 0x76.toByte() &&
                video[i + 2] == 0x68.toByte() &&
                video[i + 3] == 0x64.toByte()
            ) {
                mvhdIndex = i
                break
            }
        }

        if (mvhdIndex < 0) {
            return 500000L
        }

        // mvhd 内容从类型字段之后开始：1 字节版本 + 3 字节 flags
        val version = video[mvhdIndex + 4].toInt() and 0xFF

        fun u32(at: Int): Long {

            var v = 0L

            for (i in 0 until 4) {
                v = (v shl 8) or
                        (video[at + i].toInt() and 0xFF).toLong()
            }

            return v
        }

        val timescale: Long
        val duration: Long

        if (version == 1) {
            timescale = u32(mvhdIndex + 4 + 4 + 8 + 8)
            var d = 0L
            for (i in 0 until 8) {
                d = (d shl 8) or
                        (video[mvhdIndex + 4 + 4 + 8 + 8 + 4 + i]
                            .toInt() and 0xFF).toLong()
            }
            duration = d
        } else {
            timescale = u32(mvhdIndex + 4 + 4 + 4 + 4)
            duration = u32(mvhdIndex + 4 + 4 + 4 + 4 + 4)
        }

        if (timescale <= 0 || duration <= 0) {
            500000L
        } else {
            val halfUs = duration * 1000000L / timescale / 2
            if (halfUs > 0) halfUs else 500000L
        }

    } catch (e: Exception) {
        500000L
    }
}
