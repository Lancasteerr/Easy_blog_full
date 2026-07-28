package com.febrie.demo_bk;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "blog.scheduling.enabled=false")
class DemoBkApplicationTests {

    @Test
    void contextLoads() {
    }

}
