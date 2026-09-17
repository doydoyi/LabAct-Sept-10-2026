const API_BASE = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

async function handleResponse(response) {
  if (!response.ok) {
    let message = `Request failed with status ${response.status}`;
    try {
      const body = await response.json();
      if (body && body.error) {
        message = body.error;
      }
    } catch {
      // Response wasn't JSON - keep the generic message.
    }
    throw new Error(message);
  }
  return response.json();
}

export async function getInventory() {
  const response = await fetch(`${API_BASE}/api/inventory`);
  return handleResponse(response);
}

export async function getOrders() {
  const response = await fetch(`${API_BASE}/api/orders`);
  return handleResponse(response);
}

export async function getNotifications() {
  const response = await fetch(`${API_BASE}/api/notifications`);
  return handleResponse(response);
}

export async function placeOrder(items) {
  const response = await fetch(`${API_BASE}/api/orders`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ items }),
  });
  return handleResponse(response);
}

export async function cancelOrder(orderId) {
  const response = await fetch(`${API_BASE}/api/orders/${orderId}/cancel`, {
    method: "POST",
  });
  return handleResponse(response);
}
