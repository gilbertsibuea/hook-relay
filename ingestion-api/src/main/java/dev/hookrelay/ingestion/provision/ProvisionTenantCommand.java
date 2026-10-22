package dev.hookrelay.ingestion.provision;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Internal CLI for development tenant provisioning.
 *
 * <p>Usage:
 * {@code java -jar ingestion-api.jar provision-tenant --name="Dev Tenant"}
 *
 * <p>There is intentionally no public HTTP endpoint for tenant provisioning.
 */
@Component
@Order(0)
public class ProvisionTenantCommand implements ApplicationRunner {

    public static final String COMMAND = "provision-tenant";

    private static final Logger log = LoggerFactory.getLogger(ProvisionTenantCommand.class);

    private final TenantProvisioningService provisioningService;
    private final ConfigurableApplicationContext applicationContext;

    public ProvisionTenantCommand(
            TenantProvisioningService provisioningService,
            ConfigurableApplicationContext applicationContext) {
        this.provisioningService = provisioningService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isProvisionCommand(args)) {
            return;
        }

        String tenantName = resolveTenantName(args);
        ProvisionedTenant provisioned = provisioningService.provision(tenantName);

        // Raw key goes to stdout once for the operator. Never write it to application logs.
        log.info(
                "Provisioned tenant id={} name={} apiKeyId={} apiKeyPrefix={}",
                provisioned.tenantId(),
                provisioned.tenantName(),
                provisioned.apiKeyId(),
                provisioned.apiKeyPrefix());

        System.out.println();
        System.out.println("HookRelay tenant provisioned");
        System.out.println("  tenantId     : " + provisioned.tenantId());
        System.out.println("  tenantName   : " + provisioned.tenantName());
        System.out.println("  apiKeyId     : " + provisioned.apiKeyId());
        System.out.println("  apiKeyPrefix : " + provisioned.apiKeyPrefix());
        System.out.println("  apiKey       : " + provisioned.rawApiKey());
        System.out.println();
        System.out.println("Store the apiKey now. It will not be shown again.");
        System.out.println("Authenticate with header: X-API-Key: <apiKey>");

        int code = SpringApplication.exit(applicationContext, () -> 0);
        System.exit(code);
    }

    static boolean isProvisionCommand(ApplicationArguments args) {
        List<String> nonOption = args.getNonOptionArgs();
        return !nonOption.isEmpty() && COMMAND.equals(nonOption.getFirst());
    }

    static String resolveTenantName(ApplicationArguments args) {
        List<String> names = args.getOptionValues("name");
        if (names != null && !names.isEmpty() && names.getFirst() != null && !names.getFirst().isBlank()) {
            return names.getFirst().trim();
        }
        return "Dev Tenant";
    }
}
