# Saizeriya Automated Ordering System

## Project Purpose
A local, context-aware automated ordering system for Saizeriya designed specifically for Galaxy S24 devices. It uses on-device Large Language Models (LLMs) to infer the best menu choices based on user context (health data, weather, recent purchases) and automatically places orders.

## Target Hardware
- Samsung Galaxy S24 (Snapdragon 8 Gen 3)
- Qualcomm NPU (Hexagon) for on-device ML inference.

## Key Workflow
1. **Context Collection**: Gather data from Health Connect, Weather API, and Gmail.
2. **Menu Selection**: Feed context and Saizeriya menu JSON into an on-device LLM (Gemma 4-E2B etc.).
3. **Order Execution**: LLM outputs menu codes, which are sent to the `saizeriya.js` CLI to place the actual order.