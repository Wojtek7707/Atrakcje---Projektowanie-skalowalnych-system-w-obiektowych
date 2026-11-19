package pl.psso.attractions.service;

import static pl.psso.attractions.service.ApiException.Code.NOT_FOUND;
import static pl.psso.attractions.service.ApiException.Code.VALIDATION_ERROR;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import pl.psso.attractions.domain.Catalog.Attraction;
import pl.psso.attractions.domain.Catalog.Category;
import pl.psso.attractions.repository.CatalogRepository;

@Service
public class AttractionService {
    public record CatalogPage(List<Attraction> items, int page, int size, long totalItems, int totalPages) {}

    private final CatalogRepository repository;

    public AttractionService(CatalogRepository repository) {
        this.repository = repository;
    }

    public CatalogPage list(String category, Boolean indoor, int page, int size) {
        var catalog = repository.getCatalog();
        var categories = categoryFilter(category, catalog.categories());
        var matches = catalog.attractions().stream()
                .filter(Attraction::active)
                .filter(item -> categories.isEmpty() || categories.contains(item.category().code()))
                .filter(item -> indoor == null || item.indoor() == indoor)
                .sorted(Comparator.comparing(Attraction::id))
                .toList();
        int totalPages = Math.ceilDiv(matches.size(), size);
        var items = matches.stream().skip((long) page * size).limit(size).toList();
        return new CatalogPage(items, page, size, matches.size(), totalPages);
    }

    public Attraction find(String id) {
        return repository.getCatalog().attractions().stream()
                .filter(Attraction::active)
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ApiException(NOT_FOUND, "Attraction not found", null));
    }

    public List<Category> categories() {
        return repository.getCatalog().categories();
    }

    static Set<String> categoryFilter(String value, List<Category> categories) {
        if (value == null) {
            return Set.of();
        }
        var allowed = categories.stream().map(Category::code).collect(Collectors.toSet());
        var requested = Arrays.stream(value.split(",", -1)).map(String::strip).collect(Collectors.toSet());
        if (!allowed.containsAll(requested)) {
            throw new ApiException(VALIDATION_ERROR, "Unknown category", "category");
        }
        return requested;
    }
}
