package com.nexatech.product.query.product;

import java.util.List;

public record GetProductsByTagsQuery(List<String> tags) {}
