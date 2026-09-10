package edu.cit.alvarado;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point. Sits in the parent package edu.cit.alvarado so that component
 * scanning picks up both edu.cit.alvarado.shop (Order module) and
 * edu.cit.alvarado.inventory (Inventory module).
 */
@SpringBootApplication
public class AlvaradoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlvaradoApplication.class, args);
    }
}
