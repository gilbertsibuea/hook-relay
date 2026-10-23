package dev.hookrelay.ingestion.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.hookrelay.common.auth.TenantPrincipal;
import dev.hookrelay.ingestion.endpoint.EndpointService;
import dev.hookrelay.ingestion.endpoint.EndpointService.CreatedEndpoint;
import dev.hookrelay.ingestion.endpoint.EndpointService.EndpointView;
import dev.hookrelay.ingestion.endpoint.EndpointStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Validated
@RestController
@RequestMapping("/v1/endpoints")
public class EndpointController {

    private final EndpointService endpointService;

    public EndpointController(EndpointService endpointService) {
        this.endpointService = endpointService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EndpointCreatedResponse create(
            Authentication authentication,
            @Valid @RequestBody CreateEndpointRequest request) {
        TenantPrincipal tenant = principal(authentication);
        CreatedEndpoint created = endpointService.create(
                tenant.tenantId(),
                request.name(),
                request.url(),
                request.subscribedEventTypes());
        return EndpointCreatedResponse.from(created);
    }

    @GetMapping
    public EndpointListResponse list(Authentication authentication) {
        TenantPrincipal tenant = principal(authentication);
        List<EndpointResponse> items = endpointService.list(tenant.tenantId()).stream()
                .map(EndpointResponse::from)
                .toList();
        return new EndpointListResponse(items);
    }

    @GetMapping("/{endpointId}")
    public EndpointResponse get(Authentication authentication, @PathVariable UUID endpointId) {
        TenantPrincipal tenant = principal(authentication);
        return EndpointResponse.from(endpointService.get(tenant.tenantId(), endpointId));
    }

    @PatchMapping("/{endpointId}")
    public EndpointResponse update(
            Authentication authentication,
            @PathVariable UUID endpointId,
            @Valid @RequestBody UpdateEndpointRequest request) {
        TenantPrincipal tenant = principal(authentication);
        return EndpointResponse.from(endpointService.update(
                tenant.tenantId(),
                endpointId,
                request.name(),
                request.url(),
                request.subscribedEventTypes(),
                request.status()));
    }

    @DeleteMapping("/{endpointId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(Authentication authentication, @PathVariable UUID endpointId) {
        TenantPrincipal tenant = principal(authentication);
        endpointService.deactivate(tenant.tenantId(), endpointId);
    }

    private static TenantPrincipal principal(Authentication authentication) {
        return (TenantPrincipal) authentication.getPrincipal();
    }

    public record CreateEndpointRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 2048) String url,
            @NotEmpty List<@NotBlank @Size(max = 200) String> subscribedEventTypes) {
    }

    public record UpdateEndpointRequest(
            @Size(max = 200) String name,
            @Size(max = 2048) String url,
            List<@NotBlank @Size(max = 200) String> subscribedEventTypes,
            EndpointStatus status) {
    }

    public record EndpointResponse(
            UUID id,
            String name,
            String url,
            EndpointStatus status,
            List<String> subscribedEventTypes,
            Instant createdAt,
            Instant updatedAt) {

        static EndpointResponse from(EndpointView view) {
            return new EndpointResponse(
                    view.id(),
                    view.name(),
                    view.url(),
                    view.status(),
                    view.subscribedEventTypes(),
                    view.createdAt(),
                    view.updatedAt());
        }
    }

    public record EndpointCreatedResponse(
            UUID id,
            String name,
            String url,
            EndpointStatus status,
            List<String> subscribedEventTypes,
            Instant createdAt,
            Instant updatedAt,
            String signingSecret) {

        static EndpointCreatedResponse from(CreatedEndpoint created) {
            EndpointView view = created.endpoint();
            return new EndpointCreatedResponse(
                    view.id(),
                    view.name(),
                    view.url(),
                    view.status(),
                    view.subscribedEventTypes(),
                    view.createdAt(),
                    view.updatedAt(),
                    created.signingSecret());
        }
    }

    public record EndpointListResponse(@NotNull List<EndpointResponse> items) {
    }
}
