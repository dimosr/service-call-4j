package com.dimosr.service.build;

import com.dimosr.service.ServiceCall;

public class ServiceBuilder<REQUEST, RESPONSE> {
    ServiceCall<REQUEST, RESPONSE> call;

    public ServiceBuilder(ServiceCall<REQUEST, RESPONSE> call) {
        this.call = call;
    }

    public ServiceCall<REQUEST, RESPONSE> build() {
        return call;
    }
}
