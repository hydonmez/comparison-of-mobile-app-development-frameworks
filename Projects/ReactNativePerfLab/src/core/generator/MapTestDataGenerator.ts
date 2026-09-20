import { MapPoint } from '../../models/MapPoint';

/**
 * A synchronous utility for generating deterministic spatial datasets 
 * using purely mathematical functions.
 */
export class MapTestDataGenerator {

    /**
     * Generates a spatially distributed array of map annotations synchronously.
     * Utilizes a mathematical sine wave distribution to simulate a realistic but deterministic spread.
     *
     * @param count The total number of map points to generate. Defaults to 20.
     * @returns An array of initialized MapPoint interfaces.
     */
    static generatePoints(count: number = 20): MapPoint[] {
        // Base coordinates anchored to Istanbul for localized spatial rendering tests.
        const centerLat = 41.0082;
        const centerLon = 28.9784;
        const spread = 0.05;

        // Pre-allocate the array and use a for-loop to minimize 
        // memory allocation and callback overhead.
        const points: MapPoint[] = new Array(count);

        for (let i = 0; i < count; i++) {
            const progress = i / count;

            // Calculate linear latitude progression and sine-wave based longitude oscillation.
            const latOffset = (progress - 0.5) * spread * 2.0;
            const lonOffset = Math.sin(progress * Math.PI * 4.0) * spread;

            points[i] = {
                // Pass a deterministic integer ID for efficient UI rendering.
                id: i,
                coordinate: {
                    latitude: centerLat + latOffset,
                    longitude: centerLon + lonOffset
                },
                title: `Pin ${i + 1}`
            };
        }

        return points;
    }
}