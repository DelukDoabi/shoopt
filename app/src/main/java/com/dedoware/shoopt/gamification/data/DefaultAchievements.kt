package com.dedoware.shoopt.gamification.data

import com.dedoware.shoopt.gamification.models.Achievement
import com.dedoware.shoopt.gamification.models.AchievementCategory
import com.dedoware.shoopt.gamification.models.AchievementDifficulty

/**
 * Initialisateur des achievements par défaut
 * Permet d'ajouter facilement de nouveaux défis et objectifs
 */
object DefaultAchievements {

    fun getDefaultAchievements(): List<Achievement> {
        return listOf(
            // Achievement pour le premier produit
            Achievement(
                id = "first_product",
                title = "Premier Pas",
                description = "Ajoutez votre premier produit",
                titleResKey = "achievement_first_product_title",
                descriptionResKey = "achievement_first_product_description",
                icon = "ic_first_product",
                category = "PRODUCTS",
                xpReward = 100,
                requiredCount = 1,
                difficulty = "EASY"
            ),

            // Achievements basés sur le nombre de produits
            Achievement(
                id = "products_5",
                title = "Collectionneur",
                description = "Ajoutez 5 produits",
                titleResKey = "achievement_products_5_title",
                descriptionResKey = "achievement_products_5_description",
                icon = "ic_collection",
                category = "PRODUCTS",
                xpReward = 150,
                requiredCount = 5,
                difficulty = "EASY"
            ),

            Achievement(
                id = "products_10",
                title = "Chasseur de Prix",
                description = "Ajoutez 10 produits",
                titleResKey = "achievement_products_10_title",
                descriptionResKey = "achievement_products_10_description",
                icon = "ic_price_hunter",
                category = "PRODUCTS",
                xpReward = 250,
                requiredCount = 10,
                difficulty = "MEDIUM"
            ),

            Achievement(
                id = "products_25",
                title = "Expert Shopping",
                description = "Ajoutez 25 produits",
                titleResKey = "achievement_products_25_title",
                descriptionResKey = "achievement_products_25_description",
                icon = "ic_expert",
                category = "PRODUCTS",
                xpReward = 400,
                requiredCount = 25,
                difficulty = "MEDIUM"
            ),

            Achievement(
                id = "products_50",
                title = "Maître des Bonnes Affaires",
                description = "Ajoutez 50 produits",
                titleResKey = "achievement_products_50_title",
                descriptionResKey = "achievement_products_50_description",
                icon = "ic_master",
                category = "PRODUCTS",
                xpReward = 600,
                requiredCount = 50,
                difficulty = "HARD"
            ),

            Achievement(
                id = "products_100",
                title = "Légende du Shopping",
                description = "Ajoutez 100 produits",
                titleResKey = "achievement_products_100_title",
                descriptionResKey = "achievement_products_100_description",
                icon = "ic_legend",
                category = "PRODUCTS",
                xpReward = 1000,
                requiredCount = 100,
                difficulty = "EPIC"
            ),

            // Achievements de shopping
            Achievement(
                id = "first_shopping_session",
                title = "Premier Shopping",
                description = "Terminez votre première session de courses",
                titleResKey = "achievement_first_shopping_session_title",
                descriptionResKey = "achievement_first_shopping_session_description",
                icon = "ic_shopping_cart",
                category = "SHOPPING",
                xpReward = 75,
                requiredCount = 1,
                difficulty = "EASY"
            ),

            Achievement(
                id = "shopping_sessions_5",
                title = "Habitué du Shopping",
                description = "Terminez 5 sessions de courses",
                titleResKey = "achievement_shopping_sessions_5_title",
                descriptionResKey = "achievement_shopping_sessions_5_description",
                icon = "ic_shopping_regular",
                category = "SHOPPING",
                xpReward = 200,
                requiredCount = 5,
                difficulty = "MEDIUM"
            ),

            // Achievements d'exploration
            Achievement(
                id = "first_barcode_scan",
                title = "Scanner Novice",
                description = "Scannez votre premier code-barres",
                titleResKey = "achievement_first_barcode_scan_title",
                descriptionResKey = "achievement_first_barcode_scan_description",
                icon = "ic_barcode",
                category = "EXPLORATION",
                xpReward = 50,
                requiredCount = 1,
                difficulty = "EASY"
            ),

            Achievement(
                id = "price_comparison",
                title = "Comparateur",
                description = "Comparez les prix de plusieurs produits",
                titleResKey = "achievement_price_comparison_title",
                descriptionResKey = "achievement_price_comparison_description",
                icon = "ic_compare",
                category = "EXPLORATION",
                xpReward = 100,
                requiredCount = 1,
                difficulty = "EASY"
            ),

            // Achievements de consistance
            Achievement(
                id = "weekly_user",
                title = "Utilisateur Régulier",
                description = "Utilisez l'app 7 jours consécutifs",
                titleResKey = "achievement_weekly_user_title",
                descriptionResKey = "achievement_weekly_user_description",
                icon = "ic_calendar",
                category = "CONSISTENCY",
                xpReward = 300,
                requiredCount = 7,
                difficulty = "MEDIUM"
            ),

            // Achievements sociaux (pour futures fonctionnalités)
            Achievement(
                id = "first_share",
                title = "Partageur",
                description = "Partagez votre première trouvaille",
                titleResKey = "achievement_first_share_title",
                descriptionResKey = "achievement_first_share_description",
                icon = "ic_share",
                category = "SOCIAL",
                xpReward = 75,
                requiredCount = 1,
                difficulty = "EASY",
                isActive = false // Désactivé pour l'instant
            )
        )
    }

    /**
     * Permet d'ajouter facilement de nouveaux achievements
     */
    fun createCustomAchievement(
        id: String,
        title: String,
        description: String,
        category: AchievementCategory,
        xpReward: Int,
        requiredCount: Int = 1,
        difficulty: AchievementDifficulty = AchievementDifficulty.EASY,
        icon: String = "ic_achievement_default",
        titleResKey: String? = null,
        descriptionResKey: String? = null
    ): Achievement {
        return Achievement(
            id = id,
            title = title,
            description = description,
            titleResKey = titleResKey,
            descriptionResKey = descriptionResKey,
            icon = icon,
            category = category.name,
            xpReward = xpReward,
            requiredCount = requiredCount,
            difficulty = difficulty.name
        )
    }
}
