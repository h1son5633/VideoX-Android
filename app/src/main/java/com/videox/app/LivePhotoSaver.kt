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
// JPEG 图片末尾内嵌 MP4，并在 JPEG 里写入 Motion Photo 标准的 XMP 标记。
// 各品牌相册对该格式的识别不完全一致（用户的机器是一加/OPPO，需装机实测），
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

// 在 JPEG 的 SOI 之后插入 XMP APP1 段（Motion Photo 标记），末尾拼接 MP4
private fun buildMotionPhotoJpeg(
    jpeg: ByteArray,
    video: ByteArray
): ByteArray {

    val xmp = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" x:xmptk=\"VideoX\">" +
            "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">" +
            "<rdf:Description rdf:about=\"\"" +
            " xmlns:GCamera=\"http://ns.google.com/photos/1.0/camera/\"" +
            " xmlns:Container=\"http://ns.google.com/photos/1.0/container/\"" +
            " xmlns:Item=\"http://ns.google.com/photos/1.0/container/item/\"" +
            " GCamera:MotionPhoto=\"1\"" +
            " GCamera:MotionPhotoVersion=\"1\"" +
            " GCamera:MotionPhotoPresentationTimestampUs=\"500000\">" +
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

    val payload = header + xmp.toByteArray(Charsets.UTF_8)

    val segmentLength = payload.size + 2

    val out = ByteArrayOutputStream()

    // JPEG 以 FFD8（SOI）开头，XMP APP1 段紧跟其后
    out.write(jpeg, 0, 2)
    out.write(0xFF)
    out.write(0xE1)
    out.write((segmentLength shr 8) and 0xFF)
    out.write(segmentLength and 0xFF)
    out.write(payload)
    out.write(jpeg, 2, jpeg.size - 2)
    out.write(video)

    return out.toByteArray()
}
