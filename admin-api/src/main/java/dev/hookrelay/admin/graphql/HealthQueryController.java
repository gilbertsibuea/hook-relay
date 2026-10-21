package dev.hookrelay.admin.graphql;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class HealthQueryController {

    @QueryMapping
    public String health() {
        return "ok";
    }
}
