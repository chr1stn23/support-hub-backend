package com.chrisdev.supporthub.file.infrastructure;

import com.chrisdev.supporthub.file.application.FileStorage;
import com.chrisdev.supporthub.file.application.StorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class FileStorageConfig {

    // Para pasar a S3/MinIO: otro adaptador que implemente FileStorage, elegido por perfil
    @Bean
    FileStorage fileStorage(StorageProperties props) {
        return new LocalFileStorage(Path.of(props.path()));
    }
}
