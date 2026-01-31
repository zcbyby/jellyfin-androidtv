package org.jellyfin.androidtv.ui.browsing

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.fragment.app.Fragment
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.content
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton

data class AppInfo(
	val packageName: String,
	val appName: String,
	val icon: Drawable
)

class AppListFragment : Fragment() {

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	) = content {
		JellyfinTheme {
			val rowsFocusRequester = remember { FocusRequester() }
			LaunchedEffect(rowsFocusRequester) { rowsFocusRequester.requestFocus() }

			val rowsFragment = remember { AppListRowsSupportFragment() }
			val fragmentState by remember { mutableStateOf<AppListRowsSupportFragment?>(null) }

			androidx.compose.foundation.layout.Column {
				MainToolbar(MainToolbarActiveButton.MyApps)

				AndroidFragment<AppListRowsSupportFragment>(
					modifier = Modifier
						.focusGroup()
						.focusRequester(rowsFocusRequester)
						.focusProperties {
							onExit = {
								val isFirstRowSelected = fragmentState?.selectedPosition?.let { it <= 0 } ?: false
								if (requestedFocusDirection != FocusDirection.Up || !isFirstRowSelected) {
									cancelFocusChange()
								} else {
									fragmentState?.selectedPosition = 0
									fragmentState?.verticalGridView?.clearFocus()
								}
							}
						}
						.fillMaxSize(),
					onUpdate = { fragment ->
						fragmentState = fragment
						fragment.loadApps()
					}
				)
			}
		}
	}
}

class AppListRowsSupportFragment : RowsSupportFragment() {

	fun loadApps() {
		lifecycleScope.launch {
			val apps = withContext(Dispatchers.IO) {
				loadInstalledApps()
			}
			setupRows(apps)
		}
	}

	private suspend fun loadInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
		val pm = requireContext().packageManager
		val mainIntent = Intent(Intent.ACTION_MAIN, null)
		mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)

		val resolveInfoList = pm.queryIntentActivities(mainIntent, 0)
		val apps = mutableListOf<AppInfo>()

		for (info in resolveInfoList) {
			try {
				val appName = info.loadLabel(pm).toString()
				val icon = info.loadIcon(pm)
				apps.add(AppInfo(info.activityInfo.packageName, appName, icon))
			} catch (e: Exception) {
			}
		}

		apps.sortedBy { it.appName }
	}

	private fun setupRows(apps: List<AppInfo>) {
		val rowsAdapter = ArrayObjectAdapter(RowPresenter())
		adapter = rowsAdapter

		val presenter = AppCardPresenter()
		val listAdapter = ArrayObjectAdapter(presenter)

		apps.forEach { appInfo ->
			listAdapter.add(appInfo)
		}

		val header = HeaderItem(0, getString(R.string.lbl_my_apps))
		val row = ListRow(header, listAdapter)
		rowsAdapter.add(row)

		onItemViewClickedListener = OnItemViewClickedListener { _: Presenter.ViewHolder?, item: Any?, _: RowPresenter.ViewHolder?, _: Row? ->
			if (item is AppInfo) {
				launchApp(item)
			}
		}
	}

	private fun launchApp(appInfo: AppInfo) {
		val pm = requireContext().packageManager
		try {
			val intent = pm.getLaunchIntentForPackage(appInfo.packageName)
			intent?.let {
				startActivity(it)
			}
		} catch (e: Exception) {
		}
	}
}

class AppCardPresenter : Presenter() {
	companion object {
		private const val CARD_WIDTH = 200
		private const val CARD_HEIGHT = 200
	}

	override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
		val cardView = ImageCardView(parent.context).apply {
			isFocusable = true
			isFocusableInTouchMode = true
			setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
		}
		return ViewHolder(cardView)
	}

	override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
		val cardView = viewHolder.view as ImageCardView
		val appInfo = item as? AppInfo

		appInfo?.let {
			cardView.titleText = it.appName
			cardView.setMainImage(it.icon)
			cardView.setMainImageScaleType(ImageView.ScaleType.FIT_CENTER)
		}
	}

	override fun onUnbindViewHolder(viewHolder: ViewHolder) {
		val cardView = viewHolder.view as ImageCardView
		cardView.clearMainImage()
	}
}
