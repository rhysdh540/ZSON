package zson;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static dev.nolij.zson.Zson.*;

@State(Scope.Benchmark)
public class ZsonBench {
	static final String json;

	static {
		try {
			json = Files.readString(Path.of("spec.json5"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Benchmark
	public void spec(Blackhole bh) {
		bh.consume(parseString(json));
	}
}
