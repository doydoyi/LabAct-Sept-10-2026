const API_BASE = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

export async function placeOrder(productId, quantity) {
  const response = await fetch(`${API_BASE}/api/orders`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ productId, quantity }),
  });

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }

  return response.json();
}
