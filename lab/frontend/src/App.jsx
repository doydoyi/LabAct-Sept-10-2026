import { useState } from "react";
import { placeOrder } from "./api";

// Mirrors the seed data in sql/schema.sql. Kept in the UI so a fresh
// checkout of the repo works without an extra GET /api/inventory endpoint.
const PRODUCTS = [
  { id: "P100", label: "P100 - Wireless Mouse" },
  { id: "P200", label: "P200 - Mechanical Keyboard" },
  { id: "P300", label: "P300 - USB-C Hub" },
];

export default function App() {
  const [productId, setProductId] = useState(PRODUCTS[0].id);
  const [quantity, setQuantity] = useState(1);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    setResult(null);

    try {
      const data = await placeOrder(productId, Number(quantity));
      setResult(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="page">
      <h1>Place an Order</h1>

      <form onSubmit={handleSubmit} className="order-form">
        <label>
          Product
          <select value={productId} onChange={(e) => setProductId(e.target.value)}>
            {PRODUCTS.map((p) => (
              <option key={p.id} value={p.id}>
                {p.label}
              </option>
            ))}
          </select>
        </label>

        <label>
          Quantity
          <input
            type="number"
            min="1"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
            required
          />
        </label>

        <button type="submit" disabled={loading}>
          {loading ? "Submitting..." : "Submit Order"}
        </button>
      </form>

      {error && <div className="result result-error">Request failed: {error}</div>}

      {result && (
        <div className={`result ${result.status === "CONFIRMED" ? "result-ok" : "result-rejected"}`}>
          <p className="result-status">{result.status}</p>
          <p>{result.reason}</p>
          {result.inventory && (
            <p className="result-inventory">
              {result.inventory.name} ({result.inventory.productId}) - remaining stock:{" "}
              {result.inventory.stock}
            </p>
          )}
        </div>
      )}
    </main>
  );
}
