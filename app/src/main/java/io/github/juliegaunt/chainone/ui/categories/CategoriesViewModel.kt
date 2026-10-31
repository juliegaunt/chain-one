package io.github.juliegaunt.chainone.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.juliegaunt.chainone.appContainer
import io.github.juliegaunt.chainone.data.category.CategoryMatcher
import io.github.juliegaunt.chainone.data.category.CategorySuggestion
import io.github.juliegaunt.chainone.data.category.CategorySuggestions
import io.github.juliegaunt.chainone.data.db.Category
import io.github.juliegaunt.chainone.data.db.LibraryFilter
import io.github.juliegaunt.chainone.data.db.PatternSummary
import io.github.juliegaunt.chainone.data.repository.CategoryRepository
import io.github.juliegaunt.chainone.data.repository.PatternRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A category and how many patterns are in it now (as the library sorts them). */
data class CategoryRow(val category: Category, val patternCount: Int)

data class CategoriesUiState(
    val rows: List<CategoryRow> = emptyList(),
    /** Patterns in no category, shown under "Other". */
    val otherCount: Int = 0,
    val suggestions: List<CategorySuggestion> = emptyList(),
    /** Every pattern's title, so the category dialog can say how many a name would catch. */
    val titles: List<String> = emptyList(),
    val isLoading: Boolean = true,
)

/** The library's categories, with suggestions taken from the titles still in "Other". */
class CategoriesViewModel(
    patternRepository: PatternRepository,
    private val repository: CategoryRepository,
) : ViewModel() {

    val uiState: StateFlow<CategoriesUiState> = combine(
        patternRepository.observeLibrary(LibraryFilter()),
        repository.observeCategories(),
    ) { patterns, categories ->
        val groups = CategoryMatcher.group(patterns, categories, PatternSummary::title, PatternSummary::categoryId).toMap()
        val other = groups[null].orEmpty()
        CategoriesUiState(
            rows = categories.map { CategoryRow(it, groups[it]?.size ?: 0) },
            otherCount = other.size,
            suggestions = CategorySuggestions.from(other.map { it.title }, categories),
            titles = patterns.map { it.title },
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun add(name: String, extraWords: String = "") {
        viewModelScope.launch { repository.add(name, extraWords) }
    }

    fun update(id: Long, name: String, extraWords: String) {
        viewModelScope.launch { repository.update(id, name, extraWords) }
    }

    fun move(id: Long, by: Int) {
        viewModelScope.launch { repository.move(id, by) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { CategoriesViewModel(appContainer.patternRepository, appContainer.categoryRepository) }
        }
    }
}
