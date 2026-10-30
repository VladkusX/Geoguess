package pl.kuweta.patrol.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import pl.kuweta.patrol.R
import pl.kuweta.patrol.data.AppState
import pl.kuweta.patrol.data.Repo
import pl.kuweta.patrol.ui.AppTheme
import pl.kuweta.patrol.ui.MainActivity
import pl.kuweta.patrol.ui.ThemeState
import pl.kuweta.patrol.ui.daysWord

class KuwetaWidget : AppWidgetProvider() {

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, KuwetaWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            Repo.loadCached(context)
            val views = RemoteViews(context.packageName, R.layout.widget)
            val (sticker, main, sub) = texts(Repo.state.value)
            val t = ThemeState.theme
            val (bgRes, stickerRes) = when (t) {
                AppTheme.BRUTAL -> R.drawable.widget_bg_brutal to R.drawable.sticker_bg_brutal
                AppTheme.ARCADE -> R.drawable.widget_bg_arcade to R.drawable.sticker_bg_arcade
                AppTheme.PASTEL -> R.drawable.widget_bg_pastel to R.drawable.sticker_bg_pastel
                AppTheme.NOCKA -> R.drawable.widget_bg_nocka to R.drawable.sticker_bg_nocka
                AppTheme.PIEKLO -> R.drawable.widget_bg_pieklo to R.drawable.sticker_bg_pieklo
            }
            views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)
            views.setInt(R.id.w_sticker, "setBackgroundResource", stickerRes)
            views.setTextColor(R.id.w_sticker, t.card.toInt())
            views.setTextColor(R.id.w_main, t.line.toInt())
            views.setTextColor(R.id.w_sub, t.line.toInt())
            views.setTextViewText(R.id.w_sticker, sticker)
            views.setTextViewText(R.id.w_main, main)
            views.setTextViewText(R.id.w_sub, sub)
            val open = PendingIntent.getActivity(
                context, 1, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            manager.updateAppWidget(ids, views)
        }

        private fun texts(s: AppState?): Triple<String, String, String> {
            val duty = s?.duty ?: return Triple("KUWETA", "Otwórz aplikację", "")
            val sticker = if (duty.isMe) "TWÓJ DYŻUR" else "DYŻUR: ${duty.assigneeName.uppercase()}"
            val main = when {
                duty.daysLeft > 0 -> "Za ${duty.daysLeft} ${daysWord(duty.daysLeft)}"
                duty.daysLeft == 0 -> "Dziś!"
                else -> "Spóźnienie ${-duty.daysLeft} ${daysWord(-duty.daysLeft)}"
            }
            val myStreak = s.streaks.firstOrNull { it.userId == s.me.id }?.current ?: 0
            val sub = "🔥 $myStreak · potem ${duty.nextName} za ${duty.nextInDays} ${daysWord(duty.nextInDays)}"
            return Triple(sticker, main, sub)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
    }
}
