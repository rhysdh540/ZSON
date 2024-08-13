package zson;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import dev.nolij.zson.Zson;
import dev.nolij.zson.ZsonValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static dev.nolij.zson.Zson.*;

@State(Scope.Benchmark)
public class ZsonBench {
	static final String json;
	static final Map<String, ZsonValue> parsed;

	static {
		try {
			json = Files.readString(Path.of("spec.json5"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		parsed = parseString(json);
	}

	@Benchmark
	public void read(Blackhole bh) {
		bh.consume(parseString(json));
	}

	@Benchmark
	public void write(Blackhole bh) {
		bh.consume(new Zson().stringify(parsed));
	}
}
