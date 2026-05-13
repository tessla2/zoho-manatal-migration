package com.migration.runner;


import com.migration.service.ZohoClientService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class TestRunner implements CommandLineRunner {

    private final ZohoClientService zohoClient;

    public TestRunner(ZohoClientService zohoClient) {
        this.zohoClient = zohoClient;
    }

    @Override
    public void run(String... args) throws Exception {

        String response = zohoClient.fetchOneCandidate();

        System.out.println("ZOHO RESPONSE:");
        System.out.println(response);
    }
}
