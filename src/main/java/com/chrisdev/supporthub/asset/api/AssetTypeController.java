package com.chrisdev.supporthub.asset.api;

import com.chrisdev.supporthub.asset.application.AssetQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/asset-types")
public class AssetTypeController {

    private final AssetQueryService queryService;

    public AssetTypeController(AssetQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public List<AssetTypeResponse> list() {
        return queryService.listTypes();
    }
}
