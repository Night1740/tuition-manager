package com.tuitionmanager.feature.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tuitionmanager.feature.foundation.FoundationScreen

@Composable
fun TuitionNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = FoundationRoute,
    ) {
        composable<FoundationRoute> {
            FoundationScreen()
        }
    }
}
