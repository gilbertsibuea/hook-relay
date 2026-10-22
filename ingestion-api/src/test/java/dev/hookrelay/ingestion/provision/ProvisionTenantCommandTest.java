package dev.hookrelay.ingestion.provision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class ProvisionTenantCommandTest {

    @Test
    void detectsCommandAndDefaultName() {
        var withCommand = new DefaultApplicationArguments("provision-tenant");
        assertThat(ProvisionTenantCommand.isProvisionCommand(withCommand)).isTrue();
        assertThat(ProvisionTenantCommand.resolveTenantName(withCommand)).isEqualTo("Dev Tenant");

        var withName = new DefaultApplicationArguments("provision-tenant", "--name=Acme");
        assertThat(ProvisionTenantCommand.resolveTenantName(withName)).isEqualTo("Acme");

        var unrelated = new DefaultApplicationArguments("--server.port=0");
        assertThat(ProvisionTenantCommand.isProvisionCommand(unrelated)).isFalse();
        assertThat(unrelated.getNonOptionArgs()).isEqualTo(List.of());
    }
}
