package cucumber.runtime;

public interface StopWatch {
    void start();

    /**
     * @return nanoseconds since start. Result is undefined if start() has not been called before.
     */
    long stop();


    interface StopWatchFactory {
        StopWatch create();
    }


    class SimpleStopWatch implements StopWatch {
        private volatile long start = Long.MAX_VALUE;

        @Override
        public void start() {
            this.start = System.nanoTime();
        }

        @Override
        public long stop() {
            return  System.nanoTime() - start;
        }
    }


    StopWatchFactory SIMPLE_FACTORY = new StopWatchFactory() {
        @Override
        public StopWatch create() {
            return new SimpleStopWatch();
        }
    };



    class Stub implements StopWatch {

        public static StopWatchFactory factory(final long duration) {
            return new StopWatchFactory() {
                @Override
                public StopWatch create() {
                    return new Stub(duration);
                }
            };
        }

        private final long duration;

        public Stub(long duration) {
            this.duration = duration;
        }

        @Override
        public void start() {
        }

        @Override
        public long stop() {
            return duration;
        }
    }
}
