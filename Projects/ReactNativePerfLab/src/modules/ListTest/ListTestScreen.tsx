import React, { useRef, useMemo, useCallback } from 'react';
import { 
    View, 
    Text, 
    TouchableOpacity, 
    StyleSheet, 
    SafeAreaView,
    ActivityIndicator,
    Image,
    ImageSourcePropType
} from 'react-native';
import { FlashList, ListRenderItem } from '@shopify/flash-list';
import Icon from 'react-native-vector-icons/MaterialIcons';
import { useListTestViewModel, ListEngineController } from './useListTestViewModel';
import { Product } from '../../models/ProductData';

/**
 * Pre-allocated registry for static asset resolution to ensure O(1) lookup time.
 */
const ASSET_MAP: Record<string, ImageSourcePropType> = {
   'laptop': require('../../assets/laptop.jpg'),
    'shoe': require('../../assets/shoe.jpg'),
    'smartphone': require('../../assets/smartphone.jpg'),
    'tablet': require('../../assets/tablet.jpg'),
    'watch': require('../../assets/watch.jpg'),
    'testphoto': require('../../assets/testphoto.jpg'),
};

interface ListTestScreenProps {
    onNavigateBack: () => void;
}

/**
 * React Native UI Benchmark Screen
 */
export const ListTestScreen: React.FC<ListTestScreenProps> = ({ onNavigateBack }) => {
    
    const {
        products,
        isRunning,
        isTestCompleted,
        status,
        startTest,
        stopTest,
        exportResults
    } = useListTestViewModel();

    // Reverted to <any> to bypass broken library type exports.
    // This does not affect runtime performance.
    const listRef = useRef<any>(null);

    const listController: ListEngineController = useMemo(() => ({
        scrollToIndex: (command) => {
            listRef.current?.scrollToIndex({
                index: command.targetIndex,
                animated: command.animated,
            });
        }
    }), []);

    const renderProductItem: ListRenderItem<Product> = useCallback(({ item }) => {
        return <ProductCardView product={item} />;
    }, []);

    const renderFooter = useCallback(() => {
        if (isRunning && products.length > 0 && products.length < 300) {
            return (
                <View style={styles.footerLoader}>
                    <ActivityIndicator size="small" color="#007AFF" />
                </View>
            );
        }
        return null;
    }, [isRunning, products.length]);

    return (
        <SafeAreaView style={styles.safeArea}>
            
            <View style={styles.header}>
                <TouchableOpacity onPress={onNavigateBack} style={styles.backButton}>
                    <Text style={styles.backButtonText}>← Back</Text>
                </TouchableOpacity>
                <Text style={styles.headerTitle}>Storefront Performance</Text>
                <View style={{ width: 60 }} />
            </View>

            <View style={styles.telemetryBar}>
                <Text style={styles.statusText}>{status}</Text>
                {isRunning && <ActivityIndicator size="small" color="#8E8E93" />}
            </View>

            <View style={styles.listContainer}>
                <FlashList
                    ref={listRef}
                    data={products}
                    numColumns={2}
                    keyExtractor={(item: Product) => item.id.toString()}
                    
                    // Restored type assertion.
                    // The property exists natively and is critical for cell recycling,
                    // but the local @types package fails to expose it.
                    {...({ estimatedItemSize: 280 } as any)}
                    
                    contentContainerStyle={styles.listContent}
                    renderItem={renderProductItem}
                    ListFooterComponent={renderFooter}
                />
            </View>

            <View style={styles.controlsContainer}>
                {!isRunning ? (
                    <TouchableOpacity 
                        style={styles.primaryButton} 
                        onPress={() => startTest(listController)}
                    >
                        <Icon name="shopping-cart" size={18} color="#FFFFFF" />
                        <Text style={styles.primaryButtonText}>Start Benchmark</Text>
                    </TouchableOpacity>
                ) : (
                    <TouchableOpacity 
                        style={styles.dangerButton} 
                        onPress={() => stopTest(false)}
                    >
                        <Icon name="stop" size={18} color="#FFFFFF" />
                        <Text style={styles.primaryButtonText}>Stop Benchmark</Text>
                    </TouchableOpacity>
                )}

                {isTestCompleted && !isRunning && (
                    <TouchableOpacity 
                        style={styles.exportButton} 
                        onPress={exportResults}
                    >
                        <Icon name="system-update-alt" size={18} color="#FFFFFF" />
                        <Text style={styles.primaryButtonText}>Export Results (CSV)</Text>
                    </TouchableOpacity>
                )}
            </View>
        </SafeAreaView>
    );
};

// MARK: - Product Card Component

const ProductCardView = React.memo(({ product }: { product: Product }) => {
    
    const cleanName = useMemo(() => {
        const name = product.data.imageName;
        const lastDotIndex = name.lastIndexOf('.');
        return lastDotIndex !== -1 ? name.substring(0, lastDotIndex) : name;
    }, [product.data.imageName]);
    
    const imageSource = ASSET_MAP[cleanName];

    return (
        <View style={styles.cardContainer}>
            
            <View style={styles.imageBox}>
                {imageSource ? (
                    <Image 
                        source={imageSource}
                        style={styles.productImage}
                        resizeMode="cover"
                    />
                ) : (
                    <View style={styles.missingAssetBox}>
                        <Text style={styles.missingAssetText}>Asset Missing</Text>
                    </View>
                )}
                
                <View style={styles.discountBadge}>
                    <Text style={styles.discountText}>{product.data.discount}</Text>
                </View>
            </View>

            <View style={styles.metadataBox}>
                <View>
                    <Text style={styles.productName} numberOfLines={2}>
                        {product.data.name}
                    </Text>
                    <Text style={styles.productCategory}>
                        {product.data.category}
                    </Text>
                </View>

                <View style={styles.priceRow}>
                    <View>
                        <Text style={styles.oldPrice}>{product.data.oldPrice}</Text>
                        <Text style={styles.currentPrice}>{product.data.price}</Text>
                    </View>
                    <Icon name="add-circle" size={24} color="#007AFF" />
                </View>
            </View>
        </View>
    );
}, (prevProps, nextProps) => prevProps.product.id === nextProps.product.id);

// MARK: - Stylesheet

const styles = StyleSheet.create({
    safeArea: { flex: 1, backgroundColor: '#FFFFFF' },
    header: {
        flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
        padding: 16, borderBottomWidth: 1, borderBottomColor: '#E5E5EA',
    },
    backButton: { paddingRight: 16 },
    backButtonText: { fontSize: 18, color: '#007AFF', fontWeight: '600' },
    headerTitle: { fontSize: 18, fontWeight: 'bold', color: '#000000' },
    
    telemetryBar: {
        flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
        backgroundColor: '#F2F2F7', paddingHorizontal: 16, paddingVertical: 12,
    },
    statusText: { fontSize: 12, fontWeight: 'bold', color: '#8E8E93', flex: 1 },
    
    listContainer: { flex: 1, backgroundColor: '#F2F2F7' },
    listContent: { padding: 16, paddingBottom: 30 },
    footerLoader: { width: '100%', padding: 16, alignItems: 'center' },
    
    controlsContainer: {
        padding: 16, backgroundColor: '#FFFFFF', gap: 12,
    },
    primaryButton: {
        backgroundColor: '#007AFF', height: 56, borderRadius: 12,
        alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 8,
    },
    dangerButton: {
        backgroundColor: '#FF3B30', height: 56, borderRadius: 12,
        alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 8,
    },
    exportButton: {
        backgroundColor: '#34C759', height: 56, borderRadius: 12,
        alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 8,
    },
    primaryButtonText: { color: '#FFFFFF', fontSize: 16, fontWeight: 'bold' },
    
    cardContainer: {
        height: 280, 
        backgroundColor: '#FFFFFF',
        borderRadius: 12,
        margin: 7.5, 
        shadowColor: '#000000', shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.1, shadowRadius: 5, elevation: 5,
        overflow: 'hidden',
    },
    imageBox: {
        height: 150, backgroundColor: '#FFFFFF', alignItems: 'center', justifyContent: 'center',
    },
    productImage: {
        width: '100%', height: '100%',
    },
    missingAssetBox: {
        width: '100%', height: '100%', backgroundColor: '#D3D3D3', 
        alignItems: 'center', justifyContent: 'center'
    },
    missingAssetText: { fontSize: 10, color: '#808080' },
    discountBadge: {
        position: 'absolute', top: 8, right: 8, backgroundColor: '#FF3B30',
        paddingHorizontal: 8, paddingVertical: 4, borderRadius: 4,
    },
    discountText: { fontSize: 10, fontWeight: 'bold', color: '#FFFFFF' },
    
    metadataBox: {
        flex: 1, padding: 10, justifyContent: 'space-between',
    },
    productName: { fontSize: 14, fontWeight: '600', color: '#000000', height: 40 },
    productCategory: { fontSize: 12, color: '#8E8E93' },
    priceRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end' },
    oldPrice: { fontSize: 10, color: '#8E8E93', textDecorationLine: 'line-through', marginBottom: 2 },
    currentPrice: { fontSize: 16, fontWeight: 'bold', color: '#FF9500' },
});