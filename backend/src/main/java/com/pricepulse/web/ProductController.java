package com.pricepulse.web;

import com.pricepulse.dto.*;
import com.pricepulse.model.ObservationSource;
import com.pricepulse.model.TrackedProduct;
import com.pricepulse.service.PriceCheckService;
import com.pricepulse.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService service;
    private final PriceCheckService checker;

    public ProductController(ProductService service, PriceCheckService checker) {
        this.service = service;
        this.checker = checker;
    }

    @GetMapping
    public List<ProductResponse> list(@RequestAttribute("clientId") String clientId) {
        return service.list(clientId).stream().map(ProductResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@RequestAttribute("clientId") String clientId,
                                                  @Valid @RequestBody TrackRequest body) {
        TrackedProduct p = service.track(clientId, body);
        return ResponseEntity.created(URI.create("/api/products/" + p.getId())).body(ProductResponse.from(p));
    }

    /** Finds an existing tracked product by page URL (used by the popup to show tracking status). */
    @GetMapping("/lookup")
    public ProductResponse lookup(@RequestAttribute("clientId") String clientId, @RequestParam("url") String url) {
        return ProductResponse.from(service.lookup(clientId, url));
    }

    @GetMapping("/{id}")
    public ProductResponse get(@RequestAttribute("clientId") String clientId, @PathVariable String id) {
        return ProductResponse.from(service.get(clientId, id));
    }

    @PatchMapping("/{id}")
    public ProductResponse update(@RequestAttribute("clientId") String clientId, @PathVariable String id,
                                  @Valid @RequestBody UpdateRequest body) {
        return ProductResponse.from(service.update(clientId, id, body));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute("clientId") String clientId, @PathVariable String id) {
        service.delete(clientId, id);
    }

    @GetMapping("/{id}/history")
    public HistoryResponse history(@RequestAttribute("clientId") String clientId, @PathVariable String id,
                                   @RequestParam(defaultValue = "200") int limit) {
        return service.history(clientId, id, limit);
    }

    /** A price the extension read in the user's own browser (works on pages the server cannot read). */
    @PostMapping("/{id}/observations")
    public ProductResponse observe(@RequestAttribute("clientId") String clientId, @PathVariable String id,
                                    @Valid @RequestBody ObservationRequest body) {
        service.get(clientId, id); // ownership check
        return ProductResponse.from(service.recordObservation(id, body.price(), body.currency(), ObservationSource.BROWSER));
    }

    /** Runs a server-side check now. Responds 422 if the server cannot read a price from the page. */
    @PostMapping("/{id}/check")
    public ProductResponse checkNow(@RequestAttribute("clientId") String clientId, @PathVariable String id) {
        service.get(clientId, id);
        PriceCheckService.Result r = checker.check(id);
        if (r.outcome() != PriceCheckService.Outcome.UPDATED) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EXTRACTION_FAILED", r.message());
        }
        return ProductResponse.from(service.get(clientId, id));
    }
}
