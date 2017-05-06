package com.dimosr.service.build;

import com.dimosr.service.ServiceCall;
import org.junit.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class ServiceBuilderTest {

    @Test
    public void testBuildingPlainServiceCall() {
        ServiceCall<String, String> originalServiceCall = s -> s + " with some more data";

        ServiceCall<String, String> finalServiceCall = new ServiceBuilder<>(originalServiceCall)
                                                                                        .build();
        String request = "input";
        String response = "input with some more data";
        assertThat(finalServiceCall.call(request)).isEqualTo(response);
    }
}
