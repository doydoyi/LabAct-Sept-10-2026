package edu.cit.alvarado.shop;

import java.util.List;

/** Not exposed as JSON directly - the controller maps this to OrderResponse. */
record OrderResult(Order order, List<ItemOutcome> itemOutcomes) {
}
