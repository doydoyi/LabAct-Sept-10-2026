package edu.cit.alvarado.shop;

import java.util.List;

public record OrderRequest(List<LineItem> items) {
}
