package ifsuldeminas.edu.br.ifsystem.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IfsistemAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(IfsistemAuthApplication.class, args);
    }
}
