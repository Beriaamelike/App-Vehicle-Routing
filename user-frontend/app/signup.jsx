import {
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
  Alert,
} from "react-native";
import React, { useState } from "react";
import { useRouter } from "expo-router";
import Ionicons from "react-native-vector-icons/Ionicons";
import SimpleLineIcons from "react-native-vector-icons/SimpleLineIcons";
import { colors } from "../src/utils/colors";
import { fonts } from "../src/utils/fonts";
import DropDownPicker from "react-native-dropdown-picker";
import axios from "axios";

const SignupScreen = () => {
  const router = useRouter();
  const [secureEntry, setSecureEntry] = useState(true);
  const [role, setRole] = useState(null);
  const [open, setOpen] = useState(false);
  const [userData, setUserData] = useState({
    username: "",
    password: "",
  });

  const handleSignup = async () => {
    if (!userData.username || !userData.password || !role) {
      Alert.alert("Hata", "Lütfen tüm alanları doldurun.");
      return;
    }

    try {
      const response = await axios.post("http://10.0.2.2:8080/api/auth/register", {
        username: userData.username,
        password: userData.password,
        roles: [role], 
      });

      Alert.alert("Başarılı", "Kayıt işlemi tamamlandı!", [
        { text: "OK", onPress: () => router.push("/login") },
      ]);
    } catch (error) {
      Alert.alert("Hata", "Kayıt sırasında bir hata oluştu.");
      console.error(error);
    }
  };

  const handleGoBack = () => {
    router.back();
  };

  const handleLogin = () => {
    router.push("/login");
  };

  return (
    <View style={styles.container}>
      <TouchableOpacity style={styles.backButtonWrapper} onPress={handleGoBack}>
          <Ionicons name="arrow-back-outline" color={colors.primary} size={25} />
        </TouchableOpacity>
      <View style={styles.formContainer}>

        <View style={styles.inputContainer}>
          <Ionicons name="mail-outline" size={30} color={colors.secondary} />
          <TextInput
            style={styles.textInput}
            placeholder="Enter your email"
            placeholderTextColor={colors.secondary}
            keyboardType="email-address"
            onChangeText={(text) => setUserData({ ...userData, username: text })}
          />
        </View>
        <View style={styles.inputContainer}>
          <SimpleLineIcons name="lock" size={30} color={colors.secondary} />
          <TextInput
            style={styles.textInput}
            placeholder="Enter your password"
            placeholderTextColor={colors.secondary}
            secureTextEntry={secureEntry}
            onChangeText={(text) => setUserData({ ...userData, password: text })}
          />
          <TouchableOpacity onPress={() => setSecureEntry((prev) => !prev)}>
            <SimpleLineIcons name="eye" size={20} color={colors.secondary} />
          </TouchableOpacity>
        </View>

        <View style={styles.dropdownContainer}>
          <DropDownPicker
            open={open}
            value={role}
            items={[
              { label: "Sürücü", value: "ROLE_USER" },
              { label: "Şube Yetkilisi", value: "ROLE_ADMIN" },
            ]}
            setOpen={setOpen}
            setValue={setRole}
            placeholder="Ben Kimim?"
            style={styles.dropdown}
          />
        </View>

        <TouchableOpacity style={styles.signupButton} onPress={handleSignup}>
          <Text style={styles.signupText}>Sign up</Text>
        </TouchableOpacity>

        <View style={styles.footerContainer}>
            <Text style={styles.accountText}>Already have an account!</Text>
            <TouchableOpacity onPress={handleLogin}>
              <Text style={styles.signupText}>Login</Text>
            </TouchableOpacity>
          </View>
      </View>
    </View>
  );
};

export default SignupScreen;

const styles = StyleSheet.create({
  container: { flex: 1, padding: 20, backgroundColor: colors.white },
  formContainer: { marginTop: 20 },
  inputContainer: { flexDirection: "row", marginVertical: 10, alignItems: "center" },
  textInput: { flex: 1, marginLeft: 10 },
  signupButton: { backgroundColor: colors.primary, padding: 10, borderRadius: 10 },
  signupText: { textAlign: "center", color: colors.white },
  footerContainer: {
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    marginVertical: 20,
    gap: 5,
  },
  accountText: {
    color: colors.primary,
    fontFamily: fonts.Regular,
  },
  signupText: {
    color: colors.primary,
    fontFamily: fonts.Bold,
  },
});
