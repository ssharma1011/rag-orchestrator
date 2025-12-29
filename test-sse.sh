#!/bin/bash

# SSE Stream Test Script
# Tests the workflow SSE endpoint to verify events are being sent correctly

echo "🧪 SSE Stream Test"
echo "===================="
echo ""

# Check if conversationId provided
if [ -z "$1" ]; then
  echo "Usage: ./test-sse.sh <conversationId>"
  echo ""
  echo "Example:"
  echo "  ./test-sse.sh 0a6f387a-8b0c-4d3e-9f2a-1c5d6e7f8a9b"
  echo ""
  echo "This will connect to the SSE stream and display all events."
  exit 1
fi

CONVERSATION_ID=$1
BASE_URL=${BASE_URL:-http://localhost:8080}

echo "📡 Connecting to SSE stream..."
echo "URL: ${BASE_URL}/api/v1/workflows/${CONVERSATION_ID}/stream"
echo ""
echo "Press Ctrl+C to stop"
echo "---"
echo ""

# Connect to SSE stream and display events
curl -N -H "Accept: text/event-stream" \
  "${BASE_URL}/api/v1/workflows/${CONVERSATION_ID}/stream" \
  2>&1 | while IFS= read -r line; do
    # Colorize output for readability
    if [[ $line == event:* ]]; then
      echo -e "\033[1;34m$line\033[0m"  # Blue for event names
    elif [[ $line == data:* ]]; then
      echo -e "\033[1;32m$line\033[0m"  # Green for data
      # Pretty print JSON
      echo "$line" | sed 's/^data: //' | jq '.' 2>/dev/null && echo ""
    else
      echo "$line"
    fi
  done
