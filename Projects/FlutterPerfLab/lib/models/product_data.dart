import 'package:json_annotation/json_annotation.dart';

// Required directive for build_runner to generate compile-time parsing logic.
part 'product_data.g.dart';

/// A raw data schema representing a product entity, optimized for reflection-free JSON deserialization.
///
/// Decoupling the raw decoded properties from view-specific identifiers prevents the
/// generated parser from incurring unnecessary computational overhead during parsing.
@JsonSerializable(createToJson: false)
class ProductData {
  final String name;
  final String price;
  final String oldPrice;
  final String discount;
  final String category;
  final String imageName;

  const ProductData({
    required this.name,
    required this.price,
    required this.oldPrice,
    required this.discount,
    required this.category,
    required this.imageName,
  });

  /// Factory constructor delegating to the static, compile-time generated adapter.
  factory ProductData.fromJson(Map<String, dynamic> json) =>
      _$ProductDataFromJson(json);
}

/// A UI-bound presentation wrapper encapsulating the raw product data.
///
/// Utilizing a deterministic integer identifier bypasses the CPU overhead and Garbage
/// Collection pressure associated with UUID generation, ensuring efficient rendering benchmarks.
class Product {
  final int id;
  final ProductData data;

  const Product({required this.id, required this.data});
}
