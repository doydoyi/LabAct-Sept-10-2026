import { useEffect, useState } from "react";
import { getInventory, getOrders, getNotifications, placeOrder, cancelOrder } from "./api";

// Mirrors sql/schema.sql seed data.
const PRODUCTS = [
  { id: "P100", label: "P100 - Wireless Mouse" },
  { id: "P200", label: "P200 - Mechanical Keyboard" },
  { id: "P300", label: "P300 - USB-C Hub" },
];

// Matches the backend's default app.inventory.low-stock-threshold (5).
// If you change LOW_STOCK_THRESHOLD in the backend .env, update this too.
const LOW_STOCK_THRESHOLD = 5;

export default function App() {
  const [inventory, setInventory] = useState([]);
  const [orders, setOrders] = useState([]);
  const [notifications, setNotifications] = useState([]);

  const [cart, setCart] = useState([]);
  const [selectedProduct, setSelectedProduct] = useState(PRODUCTS[0].id);
  const [selectedQuantity, setSelectedQuantity] = useState(1);

  const [lastResult, setLastResult] = useState(null);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [cancellingId, setCancellingId] = useState(null);

  async function refreshAll() {
    try {
      const [inv, ords, notifs] = await Promise.all([getInventory(), getOrders(), getNotifications()]);
      setInventory(inv);
      setOrders(ords);
      setNotifications(notifs);
    } catch (err) {
      setError(err.message);
    }
  }

  useEffect(() => {
    refreshAll();
  }, []);

  function addToCart() {
    const quantity = Number(selectedQuantity);
    if (!quantity || quantity < 1) return;

    setCart((prev) => {
      const existing = prev.find((item) => item.productId === selectedProduct);
      if (existing) {
        return prev.map((item) =>
          item.productId === selectedProduct ? { ...item, quantity: item.quantity + quantity } : item
        );
      }
      return [...prev, { productId: selectedProduct, quantity }];
    });
  }

  function removeFromCart(productId) {
    setCart((prev) => prev.filter((item) => item.productId !== productId));
  }

  async function handleSubmitOrder() {
    if (cart.length === 0) return;
    setSubmitting(true);
    setError(null);
    setLastResult(null);

    try {
      const result = await placeOrder(cart);
      setLastResult(result);
      setCart([]);
      await refreshAll();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleCancel(orderId) {
    setCancellingId(orderId);
    setError(null);

    try {
      await cancelOrder(orderId);
      await refreshAll();
    } catch (err) {
      setError(err.message);
    } finally {
      setCancellingId(null);
    }
  }

  function productName(productId) {
    const found = inventory.find((item) => item.productId === productId);
    return found ? found.name : productId;
  }

  return (
    <main className="page">
      <h1>Order Placement</h1>

      {error && <div className="banner banner-error">{error}</div>}

      <section className="card">
        <h2>Build an order</h2>
        <div className="cart-builder">
          <select value={selectedProduct} onChange={(e) => setSelectedProduct(e.target.value)}>
            {PRODUCTS.map((p) => (
              <option key={p.id} value={p.id}>
                {p.label}
              </option>
            ))}
          </select>
          <input
            type="number"
            min="1"
            value={selectedQuantity}
            onChange={(e) => setSelectedQuantity(e.target.value)}
          />
          <button type="button" onClick={addToCart}>
            Add to cart
          </button>
        </div>

        {cart.length > 0 && (
          <ul className="cart-list">
            {cart.map((item) => (
              <li key={item.productId}>
                <span>
                  {productName(item.productId)} x {item.quantity}
                </span>
                <button type="button" className="link-button" onClick={() => removeFromCart(item.productId)}>
                  Remove
                </button>
              </li>
            ))}
          </ul>
        )}

        <button
          type="button"
          className="primary"
          disabled={cart.length === 0 || submitting}
          onClick={handleSubmitOrder}
        >
          {submitting ? "Submitting..." : "Submit Order"}
        </button>

        {lastResult && (
          <div className={`result ${lastResult.status === "CONFIRMED" ? "result-ok" : "result-rejected"}`}>
            <p className="result-status">{lastResult.status}</p>
            <p>{lastResult.reason}</p>
            <ul className="result-items">
              {lastResult.items.map((item) => (
                <li key={item.productId}>
                  {item.productId}: {item.outcome}
                </li>
              ))}
            </ul>
          </div>
        )}
      </section>

      <section className="card">
        <h2>Inventory</h2>
        <table className="table">
          <thead>
            <tr>
              <th>Product</th>
              <th>Name</th>
              <th>Stock</th>
            </tr>
          </thead>
          <tbody>
            {inventory.map((item) => (
              <tr key={item.productId} className={item.stock < LOW_STOCK_THRESHOLD ? "row-low-stock" : ""}>
                <td>{item.productId}</td>
                <td>{item.name}</td>
                <td>
                  {item.stock}
                  {item.stock < LOW_STOCK_THRESHOLD && <span className="badge badge-low">low</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="card">
        <h2>Order history</h2>
        {orders.length === 0 && <p className="muted">No orders yet.</p>}
        <ul className="order-list">
          {orders.map((order) => (
            <li key={order.orderId} className="order-row">
              <div className="order-row-header">
                <span className={`badge badge-${order.status.toLowerCase()}`}>{order.status}</span>
                <span className="order-id">Order O{order.orderId}</span>
                <span className="muted">{new Date(order.createdAt).toLocaleString()}</span>
                {order.status === "CONFIRMED" && (
                  <button
                    type="button"
                    className="link-button"
                    disabled={cancellingId === order.orderId}
                    onClick={() => handleCancel(order.orderId)}
                  >
                    {cancellingId === order.orderId ? "Cancelling..." : "Cancel"}
                  </button>
                )}
              </div>
              <div className="order-items">
                {order.items.map((item, idx) => (
                  <span key={idx} className="order-item-chip">
                    {item.productId} x {item.quantity}
                  </span>
                ))}
              </div>
              {order.reason && <p className="muted order-reason">{order.reason}</p>}
            </li>
          ))}
        </ul>
      </section>

      <section className="card">
        <h2>Activity feed</h2>
        {notifications.length === 0 && <p className="muted">No notifications yet.</p>}
        <ul className="feed-list">
          {notifications.map((n) => (
            <li key={n.notificationId} className="feed-item">
              <span>{n.message}</span>
              <span className="muted">{new Date(n.createdAt).toLocaleTimeString()}</span>
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}
