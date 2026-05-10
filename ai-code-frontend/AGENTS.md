# Frontend Project Rules

## ID Precision

- `appId` and app-related `id` values must be treated as opaque identifiers and passed through as strings.
- Never convert `appId` or app-related `id` to `Number`, `parseInt`, `parseFloat`, or any other numeric type.
- This rule applies to route params, page state, component props, table filters, form inputs, API params, and response data.
- If generated TypeScript types conflict with backend reality, widen the frontend types or use string-compatible wrappers. Do not fix type errors by converting IDs to numbers.
- For app ID inputs, use text inputs instead of numeric inputs to avoid precision loss.
