/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.execution.buffer;

import com.facebook.presto.CompressionCodec;
import com.facebook.presto.common.Page;
import com.facebook.presto.common.block.Block;
import com.facebook.presto.common.block.BlockBuilder;
import com.facebook.presto.common.type.Type;
import com.facebook.presto.spi.page.PagesSerde;
import com.google.common.collect.ImmutableList;
import io.airlift.slice.DynamicSliceOutput;
import io.airlift.slice.Slice;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;

import static com.facebook.presto.common.type.BigintType.BIGINT;
import static com.facebook.presto.common.type.VarcharType.VARCHAR;
import static com.facebook.presto.operator.PageAssertions.assertPageEquals;
import static com.facebook.presto.spi.page.PagesSerdeUtil.readPages;
import static com.facebook.presto.spi.page.PagesSerdeUtil.writePages;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestPagesSerde
{
    @DataProvider(name = "testCompressionCodec")
    public Object[][] createTestCompressionCodec()
    {
        return new Object[][] {
                {CompressionCodec.GZIP},
                {CompressionCodec.LZ4},
                {CompressionCodec.LZO},
                {CompressionCodec.SNAPPY},
                {CompressionCodec.ZLIB},
                {CompressionCodec.ZSTD},
                {CompressionCodec.NONE}
        };
    }

    @Test(dataProvider = "testCompressionCodec")
    public void testRoundTrip(CompressionCodec codec)
    {
        PagesSerde serde = new TestingPagesSerdeFactory(codec).createPagesSerde();
        BlockBuilder expectedBlockBuilder = VARCHAR.createBlockBuilder(null, 5);
        VARCHAR.writeString(expectedBlockBuilder, "alice");
        VARCHAR.writeString(expectedBlockBuilder, "bob");
        VARCHAR.writeString(expectedBlockBuilder, "charlie");
        VARCHAR.writeString(expectedBlockBuilder, "dave");
        Block expectedBlock = expectedBlockBuilder.build();

        Page expectedPage = new Page(expectedBlock, expectedBlock, expectedBlock);

        DynamicSliceOutput sliceOutput = new DynamicSliceOutput(1024);
        writePages(serde, sliceOutput, expectedPage, expectedPage, expectedPage);

        List<Type> types = ImmutableList.of(VARCHAR, VARCHAR, VARCHAR);
        Iterator<Page> pageIterator = readPages(serde, sliceOutput.slice().getInput());
        assertPageEquals(types, pageIterator.next(), expectedPage);
        assertPageEquals(types, pageIterator.next(), expectedPage);
        assertPageEquals(types, pageIterator.next(), expectedPage);
        assertFalse(pageIterator.hasNext());
    }

    @Test(dataProvider = "testCompressionCodec")
    public void testBigintSerializedSize(CompressionCodec codec)
    {
        BlockBuilder builder = BIGINT.createBlockBuilder(null, 5);

        // empty page
        Page page = new Page(builder.build());
        int pageSize = serializedSize(ImmutableList.of(BIGINT), page, codec);
        assertEquals(pageSize, 56); // page overhead ideally 35 but since a 0 sized block will be a RLEBlock we have an overhead of 13

        // page with one value
        BIGINT.writeLong(builder, 123);
        pageSize = 35; // Now we have moved to the normal block implementation so the page size overhead is 35
        page = new Page(builder.build());
        int firstValueSize = serializedSize(ImmutableList.of(BIGINT), page, codec) - pageSize;
        assertEquals(firstValueSize, 17); // value size + value overhead

        // page with two values
        BIGINT.writeLong(builder, 456);
        page = new Page(builder.build());
        int secondValueSize = serializedSize(ImmutableList.of(BIGINT), page, codec) - (pageSize + firstValueSize);
        assertEquals(secondValueSize, 8); // value size (value overhead is shared with previous value)
    }

    @Test(dataProvider = "testCompressionCodec")
    public void testVarcharSerializedSize(CompressionCodec codec)
    {
        BlockBuilder builder = VARCHAR.createBlockBuilder(null, 5);

        // empty page
        Page page = new Page(builder.build());
        int pageSize = serializedSize(ImmutableList.of(VARCHAR), page, codec);
        assertEquals(pageSize, 52); // page overhead

        // page with one value
        VARCHAR.writeString(builder, "alice");
        page = new Page(builder.build());
        int firstValueSize = serializedSize(ImmutableList.of(VARCHAR), page, codec) - pageSize;
        assertEquals(firstValueSize, 4 + 5); // length + "alice"

        // page with two values
        VARCHAR.writeString(builder, "bob");
        page = new Page(builder.build());
        int secondValueSize = serializedSize(ImmutableList.of(VARCHAR), page, codec) - (pageSize + firstValueSize);
        assertEquals(secondValueSize, 4 + 3); // length + "bob" (null shared with first entry)
    }

    @Test(dataProvider = "testCompressionCodec")
    public void testRoundTripSizeForCompactPageStaysWithinTwentyPercent(CompressionCodec codec)
    {
        PagesSerde serde = new TestingPagesSerdeFactory(codec).createPagesSerde();
        BlockBuilder variableWidthBlockBuilder1 = VARCHAR.createBlockBuilder(null, 128);
        BlockBuilder variableWidthBlockBuilder2 = VARCHAR.createBlockBuilder(null, 256);
        BlockBuilder bigintBlockBuilder = BIGINT.createBlockBuilder(null, 128);
        Block emptyVariableWidthBlock = VARCHAR.createBlockBuilder(null, 128).build();

        LongStream.range(0, 100).forEach(value -> {
            VARCHAR.writeString(variableWidthBlockBuilder1, UUID.randomUUID().toString());
            VARCHAR.writeString(variableWidthBlockBuilder2, UUID.randomUUID().toString());
            VARCHAR.writeString(variableWidthBlockBuilder2, UUID.randomUUID().toString());
            BIGINT.writeLong(bigintBlockBuilder, value);
        });
        Page compactPage = new Page(
                emptyVariableWidthBlock,
                variableWidthBlockBuilder1.build(),
                variableWidthBlockBuilder2.build(),
                bigintBlockBuilder.build())
                .compact();
        Page deserializedPage = serde.deserialize(serde.serialize(compactPage));

        double expectedMaxSize = compactPage.getRetainedSizeInBytes() * 1.2; // 120%
        double actualSize = deserializedPage.getRetainedSizeInBytes();
        assertTrue(actualSize < expectedMaxSize, "Expected round trip size difference less than 20% of original page");
    }

    private static int serializedSize(List<? extends Type> types, Page expectedPage, CompressionCodec codec)
    {
        PagesSerde serde = new TestingPagesSerdeFactory(codec).createPagesSerde();
        DynamicSliceOutput sliceOutput = new DynamicSliceOutput(1024);
        writePages(serde, sliceOutput, expectedPage);
        Slice slice = sliceOutput.slice();

        Iterator<Page> pageIterator = readPages(serde, slice.getInput());
        if (pageIterator.hasNext()) {
            assertPageEquals(types, pageIterator.next(), expectedPage);
        }
        else {
            assertEquals(expectedPage.getPositionCount(), 0);
        }
        assertFalse(pageIterator.hasNext());

        return slice.length();
    }
}
