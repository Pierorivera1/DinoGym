package io.github.pierorivera1.dinogym.ui.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The import screen as a navigation entry. [onImported] replaces it with the new routine's
 * or program's editor.
 */
@Composable
fun ImportEntry(
    shareId: String?,
    onBack: () -> Unit,
    onImported: (Imported) -> Unit,
    viewModel: ImportViewModel = hiltViewModel<ImportViewModel, ImportViewModel.Factory>(
        key = "import-$shareId",
        creationCallback = { factory -> factory.create(shareId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ImportScreen(
        state = state,
        actions = ImportActions(
            onBack = onBack,
            onPastedChange = viewModel::changePasted,
            onOpenPasted = viewModel::openPasted,
            onRetry = viewModel::retry,
            onAdd = { viewModel.add(onImported) },
        ),
    )
}
