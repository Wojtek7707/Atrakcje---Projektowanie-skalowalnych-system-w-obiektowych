package pl.psso.attractions.api;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.psso.attractions.domain.Catalog.Attraction;
import pl.psso.attractions.domain.Catalog.Category;
import pl.psso.attractions.domain.Catalog.Hours;
import pl.psso.attractions.domain.Catalog.Location;
import pl.psso.attractions.service.AttractionService;
import pl.psso.attractions.service.ProposalService;
import pl.psso.attractions.service.ProposalService.Mode;

@RestController
@RequestMapping("/api/v1")
public class AttractionController {
    public record CatalogParameters(String category, Boolean indoor, @Min(0) Integer page,
                                    @Min(1) @Max(100) Integer size) {}

    public record ProposalParameters(
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @DecimalMin("0") BigDecimal maxCost,
            @NotNull Mode mode,
            @Min(1) @Max(20) Integer people,
            String category,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal startLat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal startLng,
            @Min(1) @Max(50) Integer limit) {}

    public record AttractionSummary(String id, String name, String category, Location location, boolean indoor,
                                    int visitDurationMin, BigDecimal pricePerPerson) {
        static AttractionSummary from(Attraction attraction) {
            return new AttractionSummary(attraction.id(), attraction.name(), attraction.category().code(),
                    attraction.location(), attraction.indoor(), attraction.visitDurationMin(),
                    attraction.pricePerPerson());
        }
    }

    public record AttractionDetails(String id, String name, String category, Location location, boolean indoor,
                                    int visitDurationMin, BigDecimal pricePerPerson, String description,
                                    Map<DayOfWeek, Hours> openingHours) {
        static AttractionDetails from(Attraction attraction) {
            return new AttractionDetails(attraction.id(), attraction.name(), attraction.category().code(),
                    attraction.location(), attraction.indoor(), attraction.visitDurationMin(),
                    attraction.pricePerPerson(), attraction.description(), attraction.openingHours());
        }
    }

    public record CatalogResponse(List<AttractionSummary> items, int page, int size, long totalItems, int totalPages) {}

    private final AttractionService attractionService;
    private final ProposalService proposalService;

    public AttractionController(AttractionService attractionService, ProposalService proposalService) {
        this.attractionService = attractionService;
        this.proposalService = proposalService;
    }

    @GetMapping("/attractions")
    public CatalogResponse attractions(@Valid @ModelAttribute CatalogParameters parameters) {
        int page = parameters.page() == null ? 0 : parameters.page();
        int size = parameters.size() == null ? 20 : parameters.size();
        var result = attractionService.list(parameters.category(), parameters.indoor(), page, size);
        return new CatalogResponse(result.items().stream().map(AttractionSummary::from).toList(),
                result.page(), result.size(), result.totalItems(), result.totalPages());
    }

    @GetMapping("/attractions/{id}")
    public AttractionDetails attraction(@PathVariable String id) {
        return AttractionDetails.from(attractionService.find(id));
    }

    @GetMapping("/categories")
    public List<Category> categories() {
        return attractionService.categories();
    }

    @GetMapping("/attractions/proposals")
    public ProposalService.Result proposals(@Valid @ModelAttribute ProposalParameters parameters) {
        int people = parameters.people() == null ? 1 : parameters.people();
        int limit = parameters.limit() == null ? 10 : parameters.limit();
        return proposalService.propose(new ProposalService.Query(parameters.date(), parameters.maxCost(),
                parameters.mode(), people, parameters.category(), parameters.startLat().doubleValue(),
                parameters.startLng().doubleValue(), limit));
    }
}
