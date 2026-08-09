package com.foundry.preview.engine

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * 使用 Android LayoutInflater 直接渲染标准 XML 布局字符串。
 * 当 XmlLayoutParser 无法转换复杂/自定义 XML 时，作为回退预览模式使用。
 */
@Composable
fun XmlDirectPreview(xmlContent: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context -> inflateXmlString(context, xmlContent) },
        modifier = modifier
    )
}

private fun inflateXmlString(context: Context, xmlString: String): View {
    return try {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlString))
        val inflater = LayoutInflater.from(context)
        inflater.inflate(parser, null, false)
    } catch (e: Exception) {
        TextView(context).apply {
            text = "渲染错误: ${e.message}"
            setTextColor(0xFFFF0000.toInt())
            setPadding(32, 32, 32, 32)
        }
    }
}
