#!/bin/bash
cat app/build/reports/tests/testDebugUnitTest/classes/com.example.saizeriya.order.OrderPipelineTest.html | grep -oP '(?<=<pre>).*?(?=</pre>)'
